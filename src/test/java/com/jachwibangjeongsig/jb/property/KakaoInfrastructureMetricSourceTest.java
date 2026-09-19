package com.jachwibangjeongsig.jb.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricSource.InfrastructureKind;
import com.jachwibangjeongsig.jb.property.service.KakaoInfrastructureMetricSource;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class KakaoInfrastructureMetricSourceTest {
    private static final double LATITUDE = 37.5;
    private static final double LONGITUDE = 127.0;

    @Test
    void countUsesOfficialTotalCountWithoutEnumeratingLimitedDocuments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
            assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("KakaoAK test-key");
            assertQuery(request.getURI(), "category_group_code=CS2", "radius=500", "page=1", "size=1");
        }).andRespond(withSuccess("""
            {"meta":{"total_count":123,"pageable_count":45,"is_end":false},"documents":[{"id":"one"}]}
            """, APPLICATION_JSON));

        var source = source(builder);

        assertThat(source.measure(InfrastructureKind.CONVENIENCE_STORE, LATITUDE, LONGITUDE))
            .isEqualByComparingTo("123");
        server.verify();
    }

    @Test
    void nearestSubwayUsesFirstDistanceAndRoundsToSixDecimals() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertQuery(request.getURI(), "category_group_code=SW8", "radius=20000",
            "sort=distance", "page=1", "size=1"))
            .andRespond(withSuccess("""
                {"meta":{"total_count":2,"pageable_count":2,"is_end":false},
                 "documents":[{"id":"station","distance":"123.4567894"}]}
                """, APPLICATION_JSON));

        var source = source(builder);

        assertThat(source.measure(InfrastructureKind.SUBWAY_STATION, LATITUDE, LONGITUDE))
            .isEqualByComparingTo("123.456789");
        server.verify();
    }

    private static void assertQuery(URI uri, String... parts) {
        assertThat(uri.getPath()).isEqualTo("/v2/local/search/category.json");
        assertThat(uri.getQuery()).contains(parts).contains("x=127.0", "y=37.5");
    }

    private static KakaoInfrastructureMetricSource source(RestClient.Builder builder) {
        return new KakaoInfrastructureMetricSource(builder.baseUrl("http://localhost")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK test-key").build());
    }
}
