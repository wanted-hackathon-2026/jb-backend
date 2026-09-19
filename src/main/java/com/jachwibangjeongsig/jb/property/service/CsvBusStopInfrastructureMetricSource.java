package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricCalculator.Place;
import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricSource.InfrastructureKind;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnResource(resources = "file:${infrastructure.bus-stop-file:data/infrastructure/bus-stops.csv}")
public class CsvBusStopInfrastructureMetricSource implements InfrastructureMetricSource {
    private final Path file;
    private List<Place> cache;

    public CsvBusStopInfrastructureMetricSource(
        @Value("${infrastructure.bus-stop-file:data/infrastructure/bus-stops.csv}") Path file) {
        this.file = Files.isDirectory(file) ? file.resolve("bus-stops.csv") : file;
    }

    @Override
    public boolean supports(InfrastructureKind kind) {
        return kind == InfrastructureKind.BUS_STOP;
    }

    @Override
    public BigDecimal measure(InfrastructureKind kind, double latitude, double longitude) {
        if (!supports(kind)) throw new IllegalArgumentException("Unsupported infrastructure metric");
        return BigDecimal.valueOf(InfrastructureMetricCalculator.countWithin(500, latitude, longitude, places()));
    }

    private synchronized List<Place> places() {
        if (cache != null) return cache;
        List<Place> places = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (CSVRecord row : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                try {
                    places.add(new Place(row.get("stop_id").strip(),
                        Double.parseDouble(row.get("latitude").strip()),
                        Double.parseDouble(row.get("longitude").strip())));
                } catch (IllegalArgumentException ignored) {
                    // The approved contract excludes malformed source rows instead of failing every metric collection.
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Bus stop CSV read failed", exception);
        }
        if (places.isEmpty()) throw new IllegalStateException("Bus stop CSV contains no readable rows");
        cache = List.copyOf(places);
        return cache;
    }
}
