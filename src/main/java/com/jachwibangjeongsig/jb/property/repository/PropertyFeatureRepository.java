package com.jachwibangjeongsig.jb.property.repository;

import com.jachwibangjeongsig.jb.property.entity.PropertyFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
        INSERT INTO property_feature (id, property_id, category, metric_code, text_value, unit, computed_at)
        VALUES (:id, :propertyId, 'SUNLIGHT', 'SUNLIGHT_ESTIMATE_LEVEL', :level, 'level', :computedAt)
        ON DUPLICATE KEY UPDATE numeric_value = NULL, text_value = :level, unit = 'level', computed_at = :computedAt
        """, nativeQuery = true)
    int upsertSunlightMetric(@Param("id") UUID id, @Param("propertyId") UUID propertyId,
        @Param("level") String level, @Param("computedAt") LocalDateTime computedAt);

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

    // 지금까지 쓰기 전용이었다. 추천 프롬프트에 넣을 읽기 경로.
    List<PropertyFeature> findByPropertyIdIn(List<UUID> propertyIds);
}
