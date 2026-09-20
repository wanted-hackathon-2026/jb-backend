package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyFeatureRepository;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NoiseMetricServiceImpl implements NoiseMetricService {
    private static final Logger log = LoggerFactory.getLogger(NoiseMetricServiceImpl.class);
    private final ObjectProvider<NoiseObservationSource> sources;
    private final PropertyFeatureRepository features;
    private final TransactionTemplate transactions;

    public NoiseMetricServiceImpl(ObjectProvider<NoiseObservationSource> sources,
        PropertyFeatureRepository features, PlatformTransactionManager transactionManager) {
        this.sources = sources;
        this.features = features;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void collect(Property property) {
        if (!property.getSggCode().startsWith("11")) return;
        try {
            NoiseObservationSource source = sources.getIfAvailable();
            if (source == null) return;
            var nearest = NoiseMetricCalculator.nearest(property.getLat(), property.getLng(), source.sensors());
            if (nearest.isEmpty()) return;
            var sensor = nearest.get();
            LocalDateTime end = LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay();
            LocalDateTime start = end.minusDays(7);
            // Fetch complete observations before opening a database transaction.
            var result = NoiseMetricCalculator.summarize(sensor, start, end, source.observations(sensor, start, end));
            if (result.isEmpty()) return;
            var summary = result.get();
            BigDecimal distance = BigDecimal.valueOf(SafetyMetricCalculator.distanceMeters(property.getLat(),
                property.getLng(), new Facility(sensor.latitude(), sensor.longitude(), 1)));
            LocalDateTime computedAt = LocalDateTime.now(ZoneOffset.UTC);
            transactions.executeWithoutResult(status -> {
                save(property, "SENSOR_AVG_NOISE_7D", summary.averageDb(), null, "dB", computedAt);
                save(property, "SENSOR_DISTANCE", distance, null, "m", computedAt);
                save(property, "SENSOR_ID", null, sensor.id(), null, computedAt);
                save(property, "OBSERVATION_START", null, start.toString(), null, computedAt);
                save(property, "OBSERVATION_END", null, end.toString(), null, computedAt);
                save(property, "VALID_HOUR_COUNT_7D", BigDecimal.valueOf(summary.validHours()), null, "hour", computedAt);
            });
        } catch (RuntimeException failure) {
            // Provider messages can contain authenticated URLs; log only the exception type.
            log.warn("Noise collection failed: property={}, cause={}", property.getId(), failure.getClass().getSimpleName());
        }
    }

    private void save(Property property, String code, BigDecimal value, String text, String unit, LocalDateTime computedAt) {
        features.upsertNoiseMetric(UUID.randomUUID(), property.getId(), code, value, text, unit, computedAt);
    }
}
