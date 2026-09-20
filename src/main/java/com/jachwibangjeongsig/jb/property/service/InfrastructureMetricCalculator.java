package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InfrastructureMetricCalculator {
    private InfrastructureMetricCalculator() {}

    public static int countWithin(double radiusMeters, double latitude, double longitude, List<Place> places) {
        Set<String> countedIds = new HashSet<>();
        int count = 0;
        for (Place place : places) {
            if (place.id() == null || place.id().isBlank() || !valid(place) || !countedIds.add(place.id())) continue;
            double distance = SafetyMetricCalculator.distanceMeters(latitude, longitude,
                new Facility(place.latitude(), place.longitude(), 1));
            if (distance <= radiusMeters) count++;
        }
        return count;
    }

    private static boolean valid(Place place) {
        return Double.isFinite(place.latitude()) && Double.isFinite(place.longitude())
            && Math.abs(place.latitude()) <= 90 && Math.abs(place.longitude()) <= 180;
    }

    public record Place(String id, double latitude, double longitude) {}
}
