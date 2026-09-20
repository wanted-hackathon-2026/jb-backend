package com.jachwibangjeongsig.jb.property.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "property_feature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PropertyFeature {
    @Id @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    @Column(name = "property_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID propertyId;
    @Column(nullable = false, length = 50)
    private String category;
    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;
    @Column(name = "numeric_value", precision = 18, scale = 6)
    private BigDecimal numericValue;
    @Column(name = "text_value", length = 255)
    private String textValue;
    @Column(length = 30)
    private String unit;
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
