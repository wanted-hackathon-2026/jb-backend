package com.jachwibangjeongsig.jb.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource.Sensor;
import com.jachwibangjeongsig.jb.property.service.SeoulSdotNoiseObservationSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SeoulSdotNoiseObservationSourceTest {
    @TempDir Path directory;

    @Test
    void loadsSensorsAndPaginatesDistrictObservationsWithinRequestedPeriod() throws Exception {
        Path sensors = directory.resolve("sensors.csv");
        Files.writeString(sensors, "sensor_id,latitude,longitude,district\nnear,37.5,127.0,Gwanak-gu\n");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://example.test/key/json/IotVdata017/1/1000/Gwanak-gu/"))
            .andRespond(withSuccess(response(1001,
                row("near", "2026-09-11_00:07:00", "40") + "," + String.join(",",
                    Collections.nCopies(999, row("other", "2026-09-11_01:07:00", "90")))), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://example.test/key/json/IotVdata017/1001/2000/Gwanak-gu/"))
            .andRespond(withSuccess(response(1001,
                row("near", "2026-09-17_23:07:00", "50")), MediaType.APPLICATION_JSON));
        var source = new SeoulSdotNoiseObservationSource(builder, "http://example.test", "key", sensors, 0);
        Sensor sensor = source.sensors().getFirst();

        var observations = source.observations(sensor,
            LocalDateTime.parse("2026-09-11T00:00:00"), LocalDateTime.parse("2026-09-18T00:00:00"));

        assertThat(observations).extracting(observation -> observation.averageDb()).containsExactly("40", "50");
        server.verify();
    }

    @Test
    void rejectsDuplicateSensorIdsAndInvalidCoordinates() throws Exception {
        Path sensors = directory.resolve("sensors.csv");
        Files.writeString(sensors, "sensor_id,latitude,longitude,district\n"
            + "valid,37.5,127.0,Gwanak-gu\ninvalid,999,127.0,Gwanak-gu\nvalid,37.6,127.1,Gwanak-gu\n");

        assertThatThrownBy(() ->
            new SeoulSdotNoiseObservationSource(RestClient.builder(), "http://example.test", "key", sensors, 0))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsIncompleteApiPageInsteadOfCachingPartialObservations() throws Exception {
        Path sensors = directory.resolve("sensors.csv");
        Files.writeString(sensors, "sensor_id,latitude,longitude,district\nnear,37.5,127.0,Gwanak-gu\n");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://example.test/key/json/IotVdata017/1/1000/Gwanak-gu/"))
            .andRespond(withSuccess(response(1001, row("near", "2026-09-11_00:07:00", "40")),
                MediaType.APPLICATION_JSON));
        var source = new SeoulSdotNoiseObservationSource(builder, "http://example.test", "key", sensors, 0);
        Sensor sensor = source.sensors().getFirst();

        assertThatThrownBy(() -> source.observations(sensor,
            LocalDateTime.parse("2026-09-11T00:00:00"), LocalDateTime.parse("2026-09-18T00:00:00")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("S-DoT API returned an incomplete page");
        server.verify();
    }

    private static String response(int count, String rows) {
        return "{\"IotVdata017\":{\"list_total_count\":" + count
            + ",\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"정상 처리되었습니다\"},\"row\":[" + rows + "]}}";
    }

    private static String row(String sensor, String measuredAt, String noise) {
        return "{\"SN\":\"" + sensor + "\",\"MSRMT_HR\":\"" + measuredAt
            + "\",\"AVG_NIS\":\"" + noise + "\"}";
    }
}
