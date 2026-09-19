package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource.Observation;
import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource.Sensor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public final class NoiseMetricCalculator {
    private NoiseMetricCalculator() {}
    public record Summary(BigDecimal averageDb, int validHours) {}
    public static Optional<Sensor> nearest(double latitude, double longitude, List<Sensor> sensors) {
        Sensor nearest = null;
        double minimum = Double.POSITIVE_INFINITY;
        for (Sensor sensor : sensors) {
            if (sensor == null || sensor.id() == null || sensor.id().isBlank()) continue;
            double distance;
            try {
                distance = SafetyMetricCalculator.distanceMeters(latitude, longitude,
                    new SafetyMetricCalculator.Facility(sensor.latitude(), sensor.longitude(), 1));
            } catch (IllegalArgumentException invalidCoordinates) {
                continue;
            }
            if (distance > 500) continue;
            if (distance < minimum || (distance == minimum && sensor.id().compareTo(nearest.id()) < 0)) {
                nearest = sensor;
                minimum = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }
    public static Optional<Summary> summarize(Sensor sensor, LocalDateTime start, LocalDateTime end,
        List<Observation> observations) {
        var hours = new HashMap<LocalDateTime, BigDecimal>();
        var conflicts = new HashSet<LocalDateTime>();
        for (Observation observation : observations) {
            if (observation == null || !sensor.id().equals(observation.sensorId())
                || observation.measuredAt() == null || observation.measuredAt().isBefore(start)
                || !observation.measuredAt().isBefore(end) || observation.averageDb() == null) continue;
            BigDecimal value;
            try {
                value = new BigDecimal(observation.averageDb().strip());
            } catch (NumberFormatException invalidValue) {
                continue;
            }
            if (value.signum() <= 0) continue;
            LocalDateTime hour = observation.measuredAt().truncatedTo(ChronoUnit.HOURS);
            BigDecimal previous = hours.putIfAbsent(hour, value);
            if (previous != null && previous.compareTo(value) != 0) conflicts.add(hour);
        }
        conflicts.forEach(hours::remove);
        if (hours.size() < 84) return Optional.empty();
        BigDecimal sum = hours.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(new Summary(sum.divide(BigDecimal.valueOf(hours.size()), 6, RoundingMode.HALF_UP), hours.size()));
    }
}
