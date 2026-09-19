package com.jachwibangjeongsig.jb.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.jachwibangjeongsig.jb.property.service.InfrastructureFacilitySource.Place;
import com.jachwibangjeongsig.jb.property.service.InfrastructureMetricCalculator;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator;
import java.util.List;
import org.junit.jupiter.api.Test;

class InfrastructureMetricCalculatorContractTest {
    private static final double LAT = 37.5;
    private static final double LNG = 127.0;

    @Test
    void countsUniqueFacilitiesInsideInclusiveRadius() {
        Place boundary = new Place("boundary", 37.501, LNG);
        double radius = SafetyMetricCalculator.distanceMeters(LAT, LNG,
            new SafetyMetricCalculator.Facility(boundary.latitude(), boundary.longitude(), 1));

        assertThat(InfrastructureMetricCalculator.countWithin(radius, LAT, LNG, List.of(
            new Place("here", LAT, LNG),
            new Place("here", LAT, LNG),
            boundary,
            new Place("outside", 37.51, LNG)))).isEqualTo(2);
    }

    @Test
    void excludesMissingIdsAndInvalidCoordinates() {
        assertThat(InfrastructureMetricCalculator.countWithin(1_000, LAT, LNG, List.of(
            new Place("", LAT, LNG),
            new Place("invalid", 999, LNG),
            new Place("valid", LAT, LNG)))).isEqualTo(1);
    }

    @Test
    void choosesNearestStationWithoutRadiusLimitAndBreaksTiesById() {
        double offset = Math.toDegrees(2_000.0 / 6_371_008.8);

        assertThat(InfrastructureMetricCalculator.nearestDistance(LAT, LNG, List.of(
            new Place("b", LAT + offset, LNG),
            new Place("a", LAT + offset, LNG))).orElseThrow())
            .isEqualByComparingTo("2000.000000");
    }

    @Test
    void emptySuccessfulStationResultHasNoFakeZeroDistance() {
        assertThat(InfrastructureMetricCalculator.nearestDistance(LAT, LNG, List.of())).isEmpty();
        assertThat(InfrastructureMetricCalculator.countWithin(500, LAT, LNG, List.of())).isZero();
    }
}
