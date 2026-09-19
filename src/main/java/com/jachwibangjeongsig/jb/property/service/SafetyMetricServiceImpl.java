package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyFeatureRepository;
import com.jachwibangjeongsig.jb.property.service.SafetyFacilitySource.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SafetyMetricServiceImpl implements SafetyMetricService {
    private static final Logger log = LoggerFactory.getLogger(SafetyMetricServiceImpl.class);
    private final ObjectProvider<SafetyFacilitySource> sources;
    private final PropertyFeatureRepository features;
    private final TransactionTemplate transactions;

    public SafetyMetricServiceImpl(ObjectProvider<SafetyFacilitySource> sources,
        PropertyFeatureRepository features, PlatformTransactionManager transactionManager) {
        this.sources = sources;
        this.features = features;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void collect(Property property) {
        if (!property.getSggCode().startsWith("11")) return;
        SafetyFacilitySource source = sources.getIfAvailable();
        if (source == null) {
            log.warn("Safety collection skipped: no validated data source configured, property={}", property.getId());
            return;
        }
        for (Kind kind : Kind.values()) {
            try {
                // External I/O must complete outside the metric's database transaction.
                var facilities = source.load(kind);
                BigDecimal value;
                if (kind == Kind.POLICE_STATION) {
                    var distance = SafetyMetricCalculator.nearestDistance(property.getLat(), property.getLng(), facilities);
                    if (distance.isEmpty()) continue;
                    value = BigDecimal.valueOf(distance.getAsDouble());
                } else {
                    value = BigDecimal.valueOf(SafetyMetricCalculator.quantityWithin(500,
                        property.getLat(), property.getLng(), facilities));
                }
                transactions.executeWithoutResult(status -> features.upsertSafetyMetric(UUID.randomUUID(),
                    property.getId(), kind.metricCode, value, kind == Kind.POLICE_STATION ? "m" : "count",
                    LocalDateTime.now(ZoneOffset.UTC)));
            } catch (RuntimeException exception) {
                // Never log provider exception messages: they may contain authenticated URLs.
                log.warn("Safety collection failed: property={}, metric={}, cause={}",
                    property.getId(), kind.metricCode, exception.getClass().getSimpleName());
            }
        }
    }
}
