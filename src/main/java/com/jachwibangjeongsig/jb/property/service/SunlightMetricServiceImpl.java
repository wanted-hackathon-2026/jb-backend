package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyFeatureRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SunlightMetricServiceImpl implements SunlightMetricService {
    private static final Logger log = LoggerFactory.getLogger(SunlightMetricServiceImpl.class);
    private final PropertyFeatureRepository features;
    private final TransactionTemplate transactions;

    public SunlightMetricServiceImpl(PropertyFeatureRepository features,
        PlatformTransactionManager transactionManager) {
        this.features = features;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void collect(Property property) {
        try {
            SunlightMetricCalculator.estimate(property.getDirection(), property.getFloor(), property.getTotalFloors())
                .ifPresent(level -> transactions.executeWithoutResult(status -> features.upsertSunlightMetric(
                    UUID.randomUUID(), property.getId(), level, LocalDateTime.now(ZoneOffset.UTC))));
        } catch (RuntimeException failure) {
            log.warn("Sunlight estimation failed: property={}, cause={}",
                property.getId(), failure.getClass().getSimpleName());
        }
    }
}
