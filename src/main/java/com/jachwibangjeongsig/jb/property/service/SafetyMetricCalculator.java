package com.jachwibangjeongsig.jb.property.service;

import java.util.List;
import java.util.OptionalDouble;

/** Spherical straight-line distances, not road or walking-route distances. */
public final class SafetyMetricCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private SafetyMetricCalculator() {}

    public record Facility(double latitude, double longitude, int quantity) {
        public Facility {
            validateCoordinates(latitude, longitude);
            if (quantity < 0) throw new IllegalArgumentException("Negative facility quantity");
        }
    }

    public static double distanceMeters(double latitude, double longitude, Facility facility) {
        validateCoordinates(latitude, longitude);
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(facility.latitude());
        double sinLat = Math.sin((lat2 - lat1) / 2);
        double sinLng = Math.sin(Math.toRadians(facility.longitude() - longitude) / 2);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.clamp(a, 0, 1)));
    }

    public static long quantityWithin(double radiusMeters, double latitude, double longitude, List<Facility> facilities) {
        validateCoordinates(latitude, longitude);
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0) {
            throw new IllegalArgumentException("Invalid radius");
        }
        return facilities.stream().filter(f -> distanceMeters(latitude, longitude, f) <= radiusMeters)
            .mapToLong(Facility::quantity).sum();
    }

    public static OptionalDouble nearestDistance(double latitude, double longitude, List<Facility> facilities) {
        validateCoordinates(latitude, longitude);
        return facilities.stream().mapToDouble(f -> distanceMeters(latitude, longitude, f)).min();
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
            || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid WGS84 coordinates");
        }
    }
}
