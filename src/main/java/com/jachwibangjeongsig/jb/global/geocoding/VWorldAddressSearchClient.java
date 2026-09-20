package com.jachwibangjeongsig.jb.global.geocoding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class VWorldAddressSearchClient implements AddressSearchClient {

	private static final String BASE_URL = "https://api.vworld.kr/req/search";

	private final RestClient restClient;
	private final String apiKey;

	@Autowired
	public VWorldAddressSearchClient(@Value("${vworld.api-key}") String apiKey) {
		this(configure(RestClient.builder()).baseUrl(BASE_URL).build(), apiKey);
	}

	public VWorldAddressSearchClient(RestClient restClient, String apiKey) {
		this.restClient = restClient;
		this.apiKey = apiKey;
	}

	private static RestClient.Builder configure(RestClient.Builder builder) {
		SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
		requests.setConnectTimeout(Duration.ofSeconds(2));
		requests.setReadTimeout(Duration.ofSeconds(3));
		return builder.requestFactory(requests);
	}

	@Override
	public AddressSearchResult search(String query, int page) {
		JsonNode response = call(query, page).path("response");
		String status = response.path("status").asString("");
		if ("NOT_FOUND".equals(status)) {
			return AddressSearchResult.empty();
		}
		if (!"OK".equals(status)) {
			throw new AddressSearchUnavailableException("VWorld search returned status " + status);
		}
		return new AddressSearchResult(candidates(response), hasMore(response));
	}

	private List<AddressCandidate> candidates(JsonNode response) {
		List<AddressCandidate> candidates = new ArrayList<>();
		for (JsonNode item : response.path("result").path("items")) {
			JsonNode address = item.path("address");
			JsonNode point = item.path("point");
			String road = address.path("road").asString("");
			// crs=EPSG:4326, so x is longitude and y is latitude. Both arrive as strings.
			String x = point.path("x").asString("");
			String y = point.path("y").asString("");
			if (road.isBlank() || x.isBlank() || y.isBlank()) {
				continue;
			}
			String parcel = address.path("parcel").asString("");
			candidates.add(new AddressCandidate(
				road,
				parcel.isBlank() ? null : parcel,
				parseCoordinate(y),
				parseCoordinate(x)
			));
		}
		return List.copyOf(candidates);
	}

	private double parseCoordinate(String value) {
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			throw new AddressSearchUnavailableException("VWorld search returned a non-numeric point", exception);
		}
	}

	/** VWorld는 page.total/current를 문자열로 준다. 못 읽으면 다음 페이지가 없다고 본다. */
	private boolean hasMore(JsonNode response) {
		JsonNode page = response.path("page");
		return pageNumber(page.path("current")) < pageNumber(page.path("total"));
	}

	private int pageNumber(JsonNode node) {
		try {
			return Integer.parseInt(node.asString("").trim());
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	private JsonNode call(String query, int page) {
		try {
			JsonNode body = restClient.get()
				.uri(uri -> uri
					.queryParam("service", "search")
					.queryParam("request", "search")
					.queryParam("version", "2.0")
					.queryParam("format", "json")
					.queryParam("errorformat", "json")
					.queryParam("crs", "EPSG:4326")
					.queryParam("type", "address")
					.queryParam("category", "road")
					.queryParam("size", PAGE_SIZE)
					.queryParam("page", page)
					.queryParam("query", query)
					.queryParam("key", apiKey)
					.build())
				.retrieve()
				.body(JsonNode.class);
			if (body == null) {
				throw new AddressSearchUnavailableException("VWorld search returned an empty body");
			}
			return body;
		} catch (RestClientException exception) {
			throw new AddressSearchUnavailableException("VWorld search request failed", exception);
		}
	}
}
