package com.jachwibangjeongsig.jb.address;

import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchResult;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchUnavailableException;
import com.jachwibangjeongsig.jb.global.geocoding.VWorldAddressSearchClient;

import java.net.URI;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VWorldAddressSearchClientTest {

	private static final String TWO_ITEMS = """
		{"response":{"status":"OK",
		 "page":{"total":"3","current":"1","size":"10"},
		 "result":{"crs":"EPSG:4326","type":"ADDRESS","items":[
		   {"address":{"zipcode":"16490","category":"road",
		     "road":"경기도 수원시 팔달구 월드컵로 205",
		     "parcel":"경기도 수원시 팔달구 우만동 228"},
		    "point":{"x":"127.04339808904444","y":"37.279609852101984"}},
		   {"address":{"zipcode":"04524","category":"road",
		     "road":"서울특별시 중구 세종대로 110","parcel":""},
		    "point":{"x":"126.97839","y":"37.56661"}}]}}}
		""";

	@Test
	void mapsRoadAndParcelAndTreatsXAsLongitude() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> assertQuery(request.getURI(),
				"type=address", "category=road", "size=10", "page=1", "crs=EPSG:4326", "key=test-key"))
			.andRespond(withSuccess(TWO_ITEMS, APPLICATION_JSON)));

		AddressSearchResult result = client.search("월드컵로", 1);

		assertThat(result.candidates()).hasSize(2);
		assertThat(result.candidates().getFirst().roadAddress())
			.isEqualTo("경기도 수원시 팔달구 월드컵로 205");
		assertThat(result.candidates().getFirst().jibunAddress())
			.isEqualTo("경기도 수원시 팔달구 우만동 228");
		assertThat(result.candidates().getFirst().latitude()).isEqualTo(37.279609852101984);
		assertThat(result.candidates().getFirst().longitude()).isEqualTo(127.04339808904444);
	}

	@Test
	void reportsAnEmptyParcelAsNullRatherThanBlank() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess(TWO_ITEMS, APPLICATION_JSON)));

		assertThat(client.search("월드컵로", 1).candidates().getLast().jibunAddress()).isNull();
	}

	@Test
	void reportsHasMoreWhileTheCurrentPageIsNotTheLast() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess(TWO_ITEMS, APPLICATION_JSON)));

		assertThat(client.search("월드컵로", 1).hasMore()).isTrue();
	}

	@Test
	void reportsNoMoreOnTheLastPage() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess("""
				{"response":{"status":"OK","page":{"total":"2","current":"2","size":"10"},
				 "result":{"items":[{"address":{"road":"서울특별시 중구 세종대로 110","parcel":"서울특별시 중구 태평로1가 31"},
				  "point":{"x":"126.97839","y":"37.56661"}}]}}}
				""", APPLICATION_JSON)));

		assertThat(client.search("세종대로", 2).hasMore()).isFalse();
	}

	@Test
	void treatsNotFoundAsAnEmptyResultRatherThanAFailure() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess("""
				{"response":{"status":"NOT_FOUND"}}
				""", APPLICATION_JSON)));

		AddressSearchResult result = client.search("없는주소", 1);

		assertThat(result.candidates()).isEmpty();
		assertThat(result.hasMore()).isFalse();
	}

	@Test
	void skipsItemsMissingARoadAddressOrAPoint() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess("""
				{"response":{"status":"OK","page":{"total":"1","current":"1","size":"10"},
				 "result":{"items":[
				   {"address":{"road":"","parcel":"경기도 수원시 팔달구 우만동 228"},"point":{"x":"127.0","y":"37.2"}},
				   {"address":{"road":"서울특별시 중구 세종대로 110","parcel":""},"point":{}},
				   {"address":{"road":"경기도 수원시 팔달구 월드컵로 205","parcel":""},
				    "point":{"x":"127.04","y":"37.27"}}]}}}
				""", APPLICATION_JSON)));

		assertThat(client.search("주소", 1).candidates())
			.singleElement()
			.satisfies(candidate ->
				assertThat(candidate.roadAddress()).isEqualTo("경기도 수원시 팔달구 월드컵로 205"));
	}

	@Test
	void failsWhenVWorldReportsAnErrorStatus() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withSuccess("""
				{"response":{"status":"ERROR","error":{"text":"인증키가 유효하지 않습니다"}}}
				""", APPLICATION_JSON)));

		assertThatThrownBy(() -> client.search("월드컵로", 1))
			.isInstanceOf(AddressSearchUnavailableException.class);
	}

	@Test
	void failsWhenTheRequestItselfFails() {
		VWorldAddressSearchClient client = client(server -> server
			.expect(request -> {
			})
			.andRespond(withServerError()));

		assertThatThrownBy(() -> client.search("월드컵로", 1))
			.isInstanceOf(AddressSearchUnavailableException.class);
	}

	private static void assertQuery(URI uri, String... parts) {
		assertThat(uri.getQuery()).contains(parts);
	}

	private VWorldAddressSearchClient client(Consumer<MockRestServiceServer> expectation) {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		expectation.accept(server);
		return new VWorldAddressSearchClient(builder.baseUrl("http://localhost").build(), "test-key");
	}
}
