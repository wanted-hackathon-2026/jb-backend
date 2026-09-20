package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.property.service.NoiseMetricCalculator;
import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource.Observation;
import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource.Sensor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NoiseMetricCalculatorContractTest {
    private static final Sensor HERE = new Sensor("here", 37.5, 127);
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 11, 0, 0);
    private static final LocalDateTime END = START.plusDays(7);

    private List<Observation> hours(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Observation("here", START.plusHours(i).plusMinutes(7), i < 42 ? "40" : "60")).toList();
    }
    @Test void choosesNearestRatherThanFirstOrMostComplete() {
        assertThat(NoiseMetricCalculator.nearest(37.5, 127, List.of(new Sensor("far", 37.501, 127), HERE))).contains(HERE);
    }
    @Test void rejectsBeyond500MetersAndInvalidCoordinates() {
        assertThat(NoiseMetricCalculator.nearest(37.5, 127, List.of(new Sensor("outside", 37.51, 127), new Sensor("bad", 37.5, 216)))).isEmpty();
    }
    @Test void includesExact500MetersAndBreaksTiesById() {
        double offset = Math.toDegrees(500.0 / 6_371_008.8);
        assertThat(NoiseMetricCalculator.nearest(0, 0, List.of(new Sensor("b", offset, 0), new Sensor("a", offset, 0))).orElseThrow().id()).isEqualTo("a");
    }
    @Test void requires84DistinctHoursAndComputesArithmeticMean() {
        assertThat(NoiseMetricCalculator.summarize(HERE, START, END, hours(83))).isEmpty();
        var summary = NoiseMetricCalculator.summarize(HERE, START, END, hours(84)).orElseThrow();
        assertThat(summary.validHours()).isEqualTo(84);
        assertThat(summary.averageDb()).isEqualByComparingTo("50.000000");
    }
    @Test void duplicatesCannotInflateCoverageAndConflictingHourIsExcluded() {
        var rows = new ArrayList<>(hours(84));
        rows.addAll(hours(84));
        assertThat(NoiseMetricCalculator.summarize(HERE, START, END, rows).orElseThrow().validHours()).isEqualTo(84);
        rows.add(new Observation("here", START.plusMinutes(20), "80"));
        assertThat(NoiseMetricCalculator.summarize(HERE, START, END, rows)).isEmpty();
    }
    @Test void excludesOtherSensorsOldAndCurrentDayData() {
        var rows = new ArrayList<>(hours(83));
        rows.add(new Observation("other", START.plusHours(83), "50"));
        rows.add(new Observation("here", START.minusSeconds(1), "50"));
        rows.add(new Observation("here", END, "50"));
        assertThat(NoiseMetricCalculator.summarize(HERE, START, END, rows)).isEmpty();
        rows.add(new Observation("here", START.plusHours(83), "50"));
        assertThat(NoiseMetricCalculator.summarize(HERE, START, END, rows)).isPresent();
    }
    @Test void missingInvalidAndZeroValuesAreNotNoiseMeasurements() {
        for (String value : List.of("", "0", "-1", "NaN", "Infinity", "not-a-number")) {
            var rows = new ArrayList<>(hours(83));
            rows.add(new Observation("here", START.plusHours(83), value));
            assertThat(NoiseMetricCalculator.summarize(HERE, START, END, rows)).isEmpty();
        }
    }
    @Test void nearestSensorWithoutEnoughDataDoesNotUseFartherObservations() {
        var nearest = NoiseMetricCalculator.nearest(37.5, 127, List.of(HERE, new Sensor("other", 37.501, 127))).orElseThrow();
        var rows = hours(168).stream().map(row -> new Observation("other", row.measuredAt(), "50")).toList();
        assertThat(NoiseMetricCalculator.summarize(nearest, START, END, rows)).isEmpty();
    }
}
