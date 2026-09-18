package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyMetricCalculatorContractTest {
    private static final double LAT = 37.5;
    private static final double LNG = 127.0;
    private static final Facility HERE = new Facility(LAT, LNG, 1);
    private static final Facility NEAR = new Facility(37.501, LNG, 3);
    private static final Facility FAR = new Facility(37.51, LNG, 7);

    @Test
    void cctvAndLightsSumQuantitiesRatherThanRows() {
        assertThat(SafetyMetricCalculator.quantityWithin(500, LAT, LNG, List.of(HERE, NEAR, FAR)))
            .isEqualTo(4);
    }

    @Test
    void bellsCountIndividualFacilities() {
        assertThat(SafetyMetricCalculator.quantityWithin(500, LAT, LNG,
            List.of(HERE, new Facility(37.501, LNG, 1), FAR))).isEqualTo(2);
    }

    @Test
    void exactRadiusIsIncludedAndJustOutsideIsExcluded() {
        double boundary = SafetyMetricCalculator.distanceMeters(LAT, LNG, NEAR);
        assertThat(SafetyMetricCalculator.quantityWithin(boundary, LAT, LNG, List.of(NEAR))).isEqualTo(3);
        assertThat(SafetyMetricCalculator.quantityWithin(boundary - 0.001, LAT, LNG, List.of(NEAR))).isZero();
    }

    @Test
    void nearestStationIsNotLimitedTo500Meters() {
        assertThat(SafetyMetricCalculator.nearestDistance(LAT, LNG, List.of(FAR)).orElseThrow())
            .isBetween(1100.0, 1120.0);
    }

    @Test
    void emptySuccessfulInputMeansZeroFacilitiesButNoStationDistance() {
        assertThat(SafetyMetricCalculator.quantityWithin(500, LAT, LNG, List.of())).isZero();
        assertThat(SafetyMetricCalculator.nearestDistance(LAT, LNG, List.of()).isEmpty()).isTrue();
    }

    @Test
    void nearestStationIsSelectedRegardlessOfInputOrder() {
        assertThat(SafetyMetricCalculator.nearestDistance(LAT, LNG, List.of(FAR, NEAR)).orElseThrow())
            .isBetween(110.0, 112.0);
        assertThat(SafetyMetricCalculator.distanceMeters(LAT, LNG, HERE)).isZero();
    }
}
