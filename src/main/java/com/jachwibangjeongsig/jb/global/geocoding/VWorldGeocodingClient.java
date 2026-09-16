package com.jachwibangjeongsig.jb.global.geocoding;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Optional;

@Component
public class VWorldGeocodingClient implements GeocodingClient {

	private static final String BASE_URL = "https://api.vworld.kr/req/address";

	private final RestClient restClient;
	private final String apiKey;

	public VWorldGeocodingClient(@Value("${vworld.api-key}") String apiKey) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(3));
		this.restClient = RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(factory)
				.build();
		this.apiKey = apiKey;
	}

	@Override
	public Optional<Coordinates> locate(String roadAddress) {
		JsonNode response = call(roadAddress).path("response");
		String status = response.path("status").asString("");
		if ("NOT_FOUND".equals(status)) {
			return Optional.empty();
		}
		if (!"OK".equals(status)) {
			throw new GeocodingUnavailableException("VWorld returned status " + status);
		}
		JsonNode point = response.path("result").path("point");
		if (point.isMissingNode() || point.path("x").isMissingNode() || point.path("y").isMissingNode()) {
			throw new GeocodingUnavailableException("VWorld returned OK without a point");
		}
		// crs=epsg:4326, so x is longitude and y is latitude.
		return Optional.of(new Coordinates(point.path("y").asDouble(), point.path("x").asDouble()));
	}

	private JsonNode call(String address) {
		try {
			JsonNode body = restClient.get()
					.uri(uri -> uri
							.queryParam("service", "address")
							.queryParam("request", "getCoord")
							.queryParam("format", "json")
							.queryParam("crs", "epsg:4326")
							.queryParam("type", "road")
							.queryParam("address", address)
							.queryParam("key", apiKey)
							.build())
					.retrieve()
					.body(JsonNode.class);
			if (body == null) {
				throw new GeocodingUnavailableException("VWorld returned an empty body");
			}
			return body;
		} catch (RestClientException exception) {
			throw new GeocodingUnavailableException("VWorld request failed", exception);
		}
	}
}
