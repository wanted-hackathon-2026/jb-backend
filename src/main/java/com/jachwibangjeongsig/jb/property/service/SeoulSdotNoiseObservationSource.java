package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnExpression("'${seoul.sdot.api-key:}' != ''")
public class SeoulSdotNoiseObservationSource implements NoiseObservationSource {
    private static final DateTimeFormatter MEASURED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss");
    private static final int PAGE_SIZE = 1_000;

    private final RestClient client;
    private final String apiKey;
    private final long cacheSeconds;
    private final List<Sensor> sensors;
    private final Map<String, String> districtsBySensor;
    private final Map<String, CachedRows> cache = new HashMap<>();

    @Autowired
    public SeoulSdotNoiseObservationSource(
        @Value("${seoul.sdot.base-url:http://openapi.seoul.go.kr:8088}") String baseUrl,
        @Value("${seoul.sdot.api-key}") String apiKey,
        @Value("${seoul.sdot.sensor-file:data/noise/sensors.csv}") String sensorFile,
        @Value("${seoul.sdot.cache-seconds:3600}") long cacheSeconds) {
        this(configure(RestClient.builder()), baseUrl, apiKey, Path.of(sensorFile), cacheSeconds);
    }

    public SeoulSdotNoiseObservationSource(RestClient.Builder builder, String baseUrl,
        String apiKey, Path sensorFile, long cacheSeconds) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.cacheSeconds = cacheSeconds;
        SensorCatalogue catalogue = loadSensors(sensorFile);
        this.sensors = catalogue.sensors();
        this.districtsBySensor = catalogue.districtsBySensor();
    }

    private static RestClient.Builder configure(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(10));
        return builder.requestFactory(requests);
    }

    @Override
    public List<Sensor> sensors() {
        return sensors;
    }

    @Override
    public List<Observation> observations(Sensor sensor, LocalDateTime start, LocalDateTime end) {
        String district = districtsBySensor.get(sensor.id());
        if (district == null) throw new IllegalArgumentException("Unknown S-DoT sensor");
        return districtRows(district).stream()
            .filter(row -> sensor.id().equals(row.sensorId()))
            .map(this::toObservation)
            .filter(observation -> !observation.measuredAt().isBefore(start) && observation.measuredAt().isBefore(end))
            .toList();
    }

    private synchronized List<ApiRow> districtRows(String district) {
        // ponytail: one lock keeps the small district cache coherent; use per-district locks if registration contention appears.
        CachedRows cached = cache.get(district);
        if (cached != null && cached.fetchedAt().plusSeconds(cacheSeconds).isAfter(Instant.now())) {
            return cached.rows();
        }
        List<ApiRow> rows = new ArrayList<>();
        int start = 1;
        int total;
        do {
            JsonNode service = call(start, start + PAGE_SIZE - 1, district).path("IotVdata017");
            if (!"INFO-000".equals(service.path("RESULT").path("CODE").asString())) {
                throw new IllegalStateException("S-DoT API returned an error");
            }
            total = service.path("list_total_count").asInt(-1);
            if (total < 0 || !service.path("row").isArray()) {
                throw new IllegalStateException("S-DoT API returned an invalid response");
            }
            int expectedRows = Math.min(PAGE_SIZE, total - start + 1);
            if (service.path("row").size() != expectedRows) {
                throw new IllegalStateException("S-DoT API returned an incomplete page");
            }
            for (JsonNode row : service.path("row")) {
                rows.add(new ApiRow(row.path("SN").asString(), row.path("MSRMT_HR").asString(),
                    row.path("AVG_NIS").asString()));
            }
            start += PAGE_SIZE;
        } while (start <= total);
        List<ApiRow> result = List.copyOf(rows);
        cache.put(district, new CachedRows(Instant.now(), result));
        return result;
    }

    private JsonNode call(int start, int end, String district) {
        try {
            JsonNode body = client.get().uri("/{key}/json/IotVdata017/{start}/{end}/{district}/",
                apiKey, start, end, district).retrieve().body(JsonNode.class);
            if (body == null) throw new IllegalStateException("S-DoT API returned an empty body");
            return body;
        } catch (RestClientException exception) {
            throw new IllegalStateException("S-DoT API request failed", exception);
        }
    }

    private Observation toObservation(ApiRow row) {
        try {
            return new Observation(row.sensorId(), LocalDateTime.parse(row.measuredAt(), MEASURED_AT), row.averageDb());
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("S-DoT API returned an invalid measurement time", exception);
        }
    }

    private static SensorCatalogue loadSensors(Path file) {
        List<Sensor> sensors = new ArrayList<>();
        Map<String, String> districts = new HashMap<>();
        Set<String> ids = new HashSet<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                String id = row.get("sensor_id").strip();
                double latitude = new BigDecimal(row.get("latitude").strip()).doubleValue();
                double longitude = new BigDecimal(row.get("longitude").strip()).doubleValue();
                String district = row.get("district").strip();
                new Facility(latitude, longitude, 1);
                if (id.isEmpty() || district.isEmpty() || !ids.add(id)) {
                    throw new IllegalStateException("Invalid or duplicate S-DoT sensor");
                }
                sensors.add(new Sensor(id, latitude, longitude));
                districts.put(id, district);
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load S-DoT sensor catalogue", exception);
        }
        if (sensors.isEmpty()) throw new IllegalStateException("S-DoT sensor catalogue is empty");
        return new SensorCatalogue(List.copyOf(sensors), Map.copyOf(districts));
    }

    private record ApiRow(String sensorId, String measuredAt, String averageDb) {}
    private record CachedRows(Instant fetchedAt, List<ApiRow> rows) {}
    private record SensorCatalogue(List<Sensor> sensors, Map<String, String> districtsBySensor) {}
}
