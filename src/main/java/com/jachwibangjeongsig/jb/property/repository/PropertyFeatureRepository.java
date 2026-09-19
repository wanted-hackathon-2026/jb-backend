package com.jachwibangjeongsig.jb.property.repository;

import com.jachwibangjeongsig.jb.property.entity.PropertyFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface PropertyFeatureRepository extends JpaRepository<PropertyFeature, UUID> {
    @Modifying
    @Query(value = """
        INSERT INTO property_feature (id, property_id, category, metric_code, numeric_value, unit, computed_at)
        VALUES (:id, :propertyId, 'INFRASTRUCTURE', :metricCode, :value, :unit, :computedAt)
        ON DUPLICATE KEY UPDATE numeric_value = :value, unit = :unit, computed_at = :computedAt
        """, nativeQuery = true)
    int upsertInfrastructureMetric(@Param("id") UUID id, @Param("propertyId") UUID propertyId,
        @Param("metricCode") String metricCode, @Param("value") BigDecimal value,
        @Param("unit") String unit, @Param("computedAt") LocalDateTime computedAt);

    @Modifying
    @Query(value = """
        INSERT INTO property_feature (id, property_id, category, metric_code, numeric_value, text_value, unit, computed_at)
        VALUES (:id, :propertyId, 'NOISE', :metricCode, :value, :text, :unit, :computedAt)
        ON DUPLICATE KEY UPDATE numeric_value = :value, text_value = :text, unit = :unit, computed_at = :computedAt
        """, nativeQuery = true)
    int upsertNoiseMetric(@Param("id") UUID id, @Param("propertyId") UUID propertyId,
        @Param("metricCode") String metricCode, @Param("value") BigDecimal value, @Param("text") String text,
        @Param("unit") String unit, @Param("computedAt") LocalDateTime computedAt);

    @Modifying
    @Query(value = """
        INSERT INTO property_feature (id, property_id, category, metric_code, numeric_value, unit, computed_at)
        VALUES (:id, :propertyId, 'SAFETY', :metricCode, :value, :unit, :computedAt)
        ON DUPLICATE KEY UPDATE numeric_value = :value, unit = :unit, computed_at = :computedAt
        """, nativeQuery = true)
    int upsertSafetyMetric(@Param("id") UUID id, @Param("propertyId") UUID propertyId,
        @Param("metricCode") String metricCode, @Param("value") BigDecimal value,
        @Param("unit") String unit, @Param("computedAt") LocalDateTime computedAt);
}
