package com.jachwibangjeongsig.jb.address;

import com.jachwibangjeongsig.jb.global.geocoding.AddressCandidate;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchClient;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchResult;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchUnavailableException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key",
	"auth.cookie-secure=true",
	"auth.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(AddressSearchApiContractTest.StubAddressSearchConfiguration.class)
class AddressSearchApiContractTest {

	private static final AddressCandidate SUWON = new AddressCandidate(
		"경기도 수원시 팔달구 월드컵로 205",
		"경기도 수원시 팔달구 우만동 228",
		37.279609852101984,
		127.04339808904444
	);

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	StubAddressSearchClient search;

	@BeforeEach
	void reset() {
		search.result = new AddressSearchResult(List.of(SUWON), true);
		search.failure = null;
		search.query = null;
		search.page = 0;
	}

	@Test
	void searchesWithoutAuthenticationAndReturnsTheCandidates() throws Exception {
		mockMvc.perform(get("/api/address/search").param("query", "월드컵로"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses.length()").value(1))
			.andExpect(jsonPath("$.addresses[0].roadAddress").value(SUWON.roadAddress()))
			.andExpect(jsonPath("$.addresses[0].jibunAddress").value(SUWON.jibunAddress()))
			.andExpect(jsonPath("$.addresses[0].latitude").value(SUWON.latitude()))
			.andExpect(jsonPath("$.addresses[0].longitude").value(SUWON.longitude()))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.hasMore").value(true));

		assertThat(search.query).isEqualTo("월드컵로");
		assertThat(search.page).isEqualTo(1);
	}

	@Test
	void defaultsToTheFirstPageAndPassesAnExplicitPageThrough() throws Exception {
		mockMvc.perform(get("/api/address/search").param("query", "월드컵로"))
			.andExpect(status().isOk());
		assertThat(search.page).isEqualTo(1);

		mockMvc.perform(get("/api/address/search").param("query", "월드컵로").param("page", "3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(3));
		assertThat(search.page).isEqualTo(3);
	}

	@Test
	void returnsAnEmptyListWhenNothingMatches() throws Exception {
		search.result = AddressSearchResult.empty();

		mockMvc.perform(get("/api/address/search").param("query", "없는주소"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses.length()").value(0))
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void reportsNullForACandidateWithoutAJibunAddress() throws Exception {
		search.result = new AddressSearchResult(
			List.of(new AddressCandidate("서울특별시 중구 세종대로 110", null, 37.56661, 126.97839)), false);

		mockMvc.perform(get("/api/address/search").param("query", "세종대로"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses[0].jibunAddress").isEmpty());
	}

	@Test
	void rejectsAQueryShorterThanTwoCharacters() throws Exception {
		mockMvc.perform(get("/api/address/search").param("query", "월"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(search.query).isNull();
	}

	@Test
	void trimsTheQueryBeforeValidatingAndSearching() throws Exception {
		mockMvc.perform(get("/api/address/search").param("query", "  월드컵로  "))
			.andExpect(status().isOk());
		assertThat(search.query).isEqualTo("월드컵로");

		mockMvc.perform(get("/api/address/search").param("query", "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void rejectsAMissingQuery() throws Exception {
		mockMvc.perform(get("/api/address/search"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(search.query).isNull();
	}

	@Test
	void rejectsAPageOutsideTheAllowedRange() throws Exception {
		mockMvc.perform(get("/api/address/search").param("query", "월드컵로").param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/address/search").param("query", "월드컵로").param("page", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/address/search").param("query", "월드컵로").param("page", "first"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(search.query).isNull();
	}

	@Test
	void reportsBadGatewayWithoutLeakingTheProviderMessage() throws Exception {
		search.failure = new AddressSearchUnavailableException("인증키가 유효하지 않습니다 key=super-secret");

		mockMvc.perform(get("/api/address/search").param("query", "월드컵로"))
			.andExpect(status().isBadGateway())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("ADDRESS_SEARCH_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("주소 검색 서비스를 이용할 수 없습니다."))
			.andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("super-secret"))));
	}

	/** Lets each test decide what the provider returns without touching the network. */
	static class StubAddressSearchClient implements AddressSearchClient {

		AddressSearchResult result = AddressSearchResult.empty();
		RuntimeException failure;
		String query;
		int page;

		@Override
		public AddressSearchResult search(String query, int page) {
			this.query = query;
			this.page = page;
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubAddressSearchConfiguration {

		@Bean
		@Primary
		StubAddressSearchClient stubAddressSearchClient() {
			return new StubAddressSearchClient();
		}
	}
}
