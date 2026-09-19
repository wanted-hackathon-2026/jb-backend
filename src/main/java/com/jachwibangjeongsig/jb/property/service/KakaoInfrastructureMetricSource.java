package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricSource.InfrastructureKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnExpression("'${kakao.local.api-key:}' != ''")
public class KakaoInfrastructureMetricSource implements InfrastructureMetricSource {
    private final RestClient client;

    @Autowired
    public KakaoInfrastructureMetricSource(RestClient.Builder builder,
        @Value("${kakao.local.base-url:https://dapi.kakao.com}") String baseUrl,
        @Value("${kakao.local.api-key}") String apiKey) {
        this(configure(builder).baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey).build());
    }

    public KakaoInfrastructureMetricSource(RestClient client) {
        this.client = client;
    }

    private static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(requests);
    }

    @Override
    public boolean supports(InfrastructureKind kind) {
        return kind != InfrastructureKind.BUS_STOP;
    }

    @Override
    public BigDecimal measure(InfrastructureKind kind, double latitude, double longitude) {
        if (!supports(kind)) throw new IllegalArgumentException("Unsupported infrastructure metric");
        JsonNode response = request(kind, latitude, longitude);
        if (kind == InfrastructureKind.SUBWAY_STATION) return subwayDistance(response);
        JsonNode totalCount = response.path("meta").path("total_count");
        if (!totalCount.isIntegralNumber() || totalCount.asLong() < 0) {
            throw new IllegalStateException("Kakao Local API returned an invalid total count");
        }
        return BigDecimal.valueOf(totalCount.asLong());
    }

    private JsonNode request(InfrastructureKind kind, double latitude, double longitude) {
        try {
            JsonNode body = client.get().uri(builder -> builder.path("/v2/local/search/category.json")
                .queryParam("category_group_code", category(kind))
                .queryParam("x", longitude).queryParam("y", latitude)
                .queryParam("radius", radius(kind)).queryParam("page", 1).queryParam("size", 1)
                .queryParamIfPresent("sort", kind == InfrastructureKind.SUBWAY_STATION
                    ? Optional.of("distance") : Optional.empty())
                .build()).retrieve().body(JsonNode.class);
            if (body == null || !body.path("meta").isObject() || !body.path("documents").isArray()) {
                throw new IllegalStateException("Kakao Local API returned an invalid response");
            }
            return body;
        } catch (RestClientException exception) {
            throw new IllegalStateException("Kakao Local API request failed", exception);
        }
    }

    private static BigDecimal subwayDistance(JsonNode response) {
        JsonNode documents = response.path("documents");
        if (documents.isEmpty()) throw new IllegalStateException("Kakao Local API returned no subway station");
        try {
            BigDecimal distance = new BigDecimal(documents.get(0).path("distance").asString());
            if (distance.signum() < 0) throw new NumberFormatException("Negative distance");
            return distance.setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Kakao Local API returned an invalid distance", exception);
        }
    }

    private static String category(InfrastructureKind kind) {
        return switch (kind) {
            case SUBWAY_STATION -> "SW8";
            case CONVENIENCE_STORE -> "CS2";
            case LARGE_MART -> "MT1";
            case HOSPITAL -> "HP8";
            case PHARMACY -> "PM9";
            case BUS_STOP -> throw new IllegalArgumentException("Unsupported infrastructure metric");
        };
    }

    private static int radius(InfrastructureKind kind) {
        return switch (kind) {
            case CONVENIENCE_STORE -> 500;
            case SUBWAY_STATION -> 20_000;
            default -> 1_000;
        };
    }
}
