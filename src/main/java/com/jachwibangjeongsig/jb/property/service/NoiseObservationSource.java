package com.jachwibangjeongsig.jb.property.service;

import java.time.LocalDateTime;
import java.util.List;

/** Return the complete current sensor catalogue and complete observations, or throw. */
public interface NoiseObservationSource {
    record Sensor(String id, double latitude, double longitude) {}
    record Observation(String sensorId, LocalDateTime measuredAt, String averageDb) {}
    List<Sensor> sensors();
    List<Observation> observations(Sensor sensor, LocalDateTime start, LocalDateTime end);
}
