package com.jachwibangjeongsig.jb.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.jachwibangjeongsig.jb.property.service.NoiseMetricCalculator;
import com.jachwibangjeongsig.jb.property.service.SeoulSdotNoiseObservationSource;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

@EnabledIfEnvironmentVariable(named = "SEOUL_SDOT_LIVE_TEST", matches = "true")
class SeoulSdotNoiseObservationSourceLiveTest {
    @Test
    void fetchesCompletedSevenDaysForNearestBongcheonSensor() {
        String key = System.getenv("SEOUL_OPEN_API_KEY");
        assertThat(key).isNotBlank();
        var source = new SeoulSdotNoiseObservationSource(RestClient.builder(),
            "http://openapi.seoul.go.kr:8088", key, Path.of("data/noise/sensors.csv"), 3_600);
        var sensor = NoiseMetricCalculator.nearest(37.48022644989664, 126.94434139377387, source.sensors())
            .orElseThrow();
        var end = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();
        var observations = source.observations(sensor, end.minusDays(7), end);

        assertThat(NoiseMetricCalculator.summarize(sensor, end.minusDays(7), end, observations)).isPresent();
    }
}
