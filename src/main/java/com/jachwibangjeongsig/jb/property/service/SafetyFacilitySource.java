package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import java.util.List;

/** Adapters must return complete, validated Seoul data in WGS84 or throw. */
public interface SafetyFacilitySource {
    enum Kind {
        CCTV("CCTV_COUNT_500M"),
        EMERGENCY_BELL("EMERGENCY_BELL_COUNT_500M"),
        SECURITY_LIGHT("SECURITY_LIGHT_COUNT_500M"),
        POLICE_STATION("NEAREST_POLICE_STATION_DISTANCE");

        public final String metricCode;
        Kind(String metricCode) { this.metricCode = metricCode; }
    }

    List<Facility> load(Kind kind);
}
