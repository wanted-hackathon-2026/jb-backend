package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyFeatureRepository;
import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricSource.InfrastructureKind;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InfrastructureMetricServiceImpl implements InfrastructureMetricService {
    private static final Logger log = LoggerFactory.getLogger(InfrastructureMetricServiceImpl.class);
    private final ObjectProvider<InfrastructureMetricSource> sources;
    private final PropertyFeatureRepository features;
    private final TransactionTemplate transactions;

    public InfrastructureMetricServiceImpl(ObjectProvider<InfrastructureMetricSource> sources,
        PropertyFeatureRepository features, PlatformTransactionManager transactionManager) {
        this.sources = sources;
        this.features = features;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void collect(Property property) {
        if (!property.getSggCode().startsWith("11")) return;
        for (InfrastructureKind kind : InfrastructureKind.values()) {
            try {
                Optional<InfrastructureMetricSource> source = sources.orderedStream()
                    .filter(candidate -> candidate.supports(kind)).findFirst();
                if (source.isEmpty()) continue;
                BigDecimal value = source.get().measure(kind, property.getLat(), property.getLng());
                if (value == null || value.signum() < 0) throw new IllegalStateException("Invalid infrastructure value");
                LocalDateTime computedAt = LocalDateTime.now(ZoneOffset.UTC);
                transactions.executeWithoutResult(status -> features.upsertInfrastructureMetric(UUID.randomUUID(),
                    property.getId(), kind.metricCode, value, kind.unit, computedAt));
            } catch (RuntimeException exception) {
                log.warn("Infrastructure collection failed: property={}, metric={}, cause={}",
                    property.getId(), kind.metricCode, exception.getClass().getSimpleName());
            }
        }
    }
}
