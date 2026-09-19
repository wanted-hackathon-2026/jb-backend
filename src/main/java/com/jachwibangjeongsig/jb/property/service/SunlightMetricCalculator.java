package com.jachwibangjeongsig.jb.property.service;

import java.util.Optional;

public final class SunlightMetricCalculator {
    private SunlightMetricCalculator() {}

    public static Optional<String> estimate(String direction, Integer floor, Integer totalFloors) {
        if (direction == null || floor == null || totalFloors == null || totalFloors <= 0 || floor > totalFloors) {
            return Optional.empty();
        }
        if (floor <= 1) return supported(direction) ? Optional.of("LOW") : Optional.empty();

        boolean upperFloor = (long) floor * 2 >= totalFloors;
        return switch (direction) {
            case "남향", "남동향", "남서향" -> Optional.of(upperFloor ? "GOOD" : "NORMAL");
            case "동향", "서향" -> Optional.of("NORMAL");
            case "북동향", "북서향" -> Optional.of(upperFloor ? "NORMAL" : "LOW");
            case "북향" -> Optional.of("LOW");
            default -> Optional.empty();
        };
    }

    private static boolean supported(String direction) {
        return switch (direction) {
            case "남향", "남동향", "남서향", "동향", "서향", "북동향", "북서향", "북향" -> true;
            default -> false;
        };
    }
}
