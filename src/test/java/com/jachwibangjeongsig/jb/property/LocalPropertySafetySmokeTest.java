package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.global.geocoding.VWorldGeocodingClient;
import com.jachwibangjeongsig.jb.property.service.CsvSafetyFacilitySource;
import com.jachwibangjeongsig.jb.property.service.SafetyFacilitySource;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit opt-in: leaves one labeled property and its metrics in the local DB. Never clears existing data. */
@EnabledIfEnvironmentVariable(named = "SAFETY_LOCAL_DB_SMOKE", matches = "true")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3307/jb",
    "safety.csv.enabled=false",
    "auth.google-client-id=local-smoke-client",
    "auth.access-token-secret=local-smoke-access-secret-at-least-32-bytes",
    "auth.refresh-token-secret=local-smoke-refresh-secret-at-least-32-bytes",
    "vworld.api-key=local-smoke-no-external-calls"
})
@AutoConfigureMockMvc
@Import(LocalPropertySafetySmokeTest.Fixtures.class)
class LocalPropertySafetySmokeTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;

    @Test
    void leaveOneLabeledPropertyWithMockSafetyMetrics() throws Exception {
        String marker = UUID.randomUUID().toString();
        boolean real = "true".equals(System.getenv("SAFETY_LOCAL_REAL_ADDRESS"));
        String name = (real ? "검증용 봉천동 919-19 " : "검증용 치안 목데이터 ") + marker.substring(0, 8);
        String address = real ? "서울특별시 관악구 봉천동 919-19" : "서울특별시 중구 검증용 가상주소";
        User temporaryAdmin = users.save(User.builder().provider("google")
            .providerId("local-smoke-" + marker).email("local-smoke-" + marker + "@example.invalid")
            .nickname("검증" + marker.substring(0, 8)).role(UserRole.ADMIN).build());
        try {
            String token = tokens.issue(temporaryAdmin, UUID.randomUUID(), Instant.now()).accessToken();
            mvc.perform(post("/api/properties").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("""
                    {"name":"%s","address":"%s",
                    "roadAddress":"%s","sggCode":"%s",
                    "umdName":"%s","propertyType":"원룸","leaseType":"MONTHLY",
                    "deposit":1000,"monthlyRent":50,"description":"검증용 매물. 가격과 매물 조건은 가상이며 실제 매물이 아님."}
                    """.formatted(name, address, address, real ? "11620" : "11140", real ? "봉천동" : "태평로1가"))).andExpect(status().isCreated());
            String id = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM property WHERE name = ?", String.class, name);
            System.out.println("LOCAL_SMOKE_PROPERTY_ID=" + id);
            System.out.println("LOCAL_SMOKE_PROPERTY_NAME=" + name);
            if (real) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE property_id=UUID_TO_BIN(?) AND metric_code IN ('CCTV_COUNT_500M','EMERGENCY_BELL_COUNT_500M')", Long.class, id)).isEqualTo(2);
                return;
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE property_id=UUID_TO_BIN(?)", Long.class, id))
                .isEqualTo(3);
            assertMetric(id, "CCTV_COUNT_500M", "5");
            assertMetric(id, "EMERGENCY_BELL_COUNT_500M", "2");
            assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE property_id=UUID_TO_BIN(?) AND metric_code='NEAREST_POLICE_STATION_DISTANCE'",
                BigDecimal.class, id)).isBetween(new BigDecimal("110"), new BigDecimal("112"));
        } finally {
            users.deleteById(temporaryAdmin.getId());
        }
    }

    private void assertMetric(String id, String code, String expected) {
        assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE property_id=UUID_TO_BIN(?) AND metric_code=?",
            BigDecimal.class, id, code)).isEqualByComparingTo(expected);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean @Primary
        GeocodingClient localSmokeCoordinates() {
            if ("true".equals(System.getenv("SAFETY_LOCAL_REAL_ADDRESS"))) {
                GeocodingClient live = new VWorldGeocodingClient(System.getenv("VWORLD_API_KEY"));
                return address -> address.equals("서울특별시 관악구 봉천동 919-19")
                    ? Optional.of(new Coordinates(37.48022644989664, 126.94434139377387)) : live.locate(address);
            }
            return address -> Optional.of(new Coordinates(37.5663, 126.978));
        }

        @Bean
        SafetyFacilitySource localSmokeFacilities() {
            if ("true".equals(System.getenv("SAFETY_LOCAL_REAL_ADDRESS"))) {
                return new CsvSafetyFacilitySource(Path.of("data/safety"), localSmokeCoordinates());
            }
            return kind -> switch (kind) {
                case CCTV -> List.of(new Facility(37.5663, 126.978, 2), new Facility(37.5663, 126.978, 3));
                case EMERGENCY_BELL -> List.of(new Facility(37.5663, 126.978, 1), new Facility(37.5663, 126.978, 1));
                case POLICE_STATION -> List.of(new Facility(37.5673, 126.978, 1));
                case SECURITY_LIGHT -> throw new IllegalStateException("No mock light dataset");
            };
        }
    }
}
