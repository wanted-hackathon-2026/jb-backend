package com.jachwibangjeongsig.jb.property.service;

import java.math.BigDecimal;

public interface InfrastructureMetricSource {
    enum InfrastructureKind {
        SUBWAY_STATION("NEAREST_SUBWAY_STATION_DISTANCE", "m"),
        BUS_STOP("BUS_STOP_COUNT_500M", "count"),
        CONVENIENCE_STORE("CONVENIENCE_STORE_COUNT_500M", "count"),
        LARGE_MART("LARGE_MART_COUNT_1KM", "count"),
        HOSPITAL("HOSPITAL_COUNT_1KM", "count"),
        PHARMACY("PHARMACY_COUNT_1KM", "count");

        public final String metricCode;
        public final String unit;

        InfrastructureKind(String metricCode, String unit) {
            this.metricCode = metricCode;
            this.unit = unit;
        }
    }

    boolean supports(InfrastructureKind kind);

    BigDecimal measure(InfrastructureKind kind, double latitude, double longitude);
}
