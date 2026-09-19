package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.property.service.SunlightMetricCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SunlightMetricCalculatorTest {

    @ParameterizedTest
    @CsvSource({
        "남향,5,10,GOOD",
        "남동향,4,8,GOOD",
        "남서향,4,10,NORMAL",
        "동향,9,10,NORMAL",
        "서향,2,10,NORMAL",
        "북동향,5,10,NORMAL",
        "북서향,4,10,LOW",
        "북향,9,10,LOW",
        "남향,1,10,LOW",
        "남향,-1,10,LOW"
    })
    void estimatesLevelFromDirectionAndFloorPosition(String direction, int floor, int totalFloors,
        String expected) {
        assertThat(SunlightMetricCalculator.estimate(direction, floor, totalFloors)).contains(expected);
    }

    @Test
    void rejectsMissingUnsupportedOrContradictoryInputs() {
        assertThat(SunlightMetricCalculator.estimate(null, 5, 10)).isEmpty();
        assertThat(SunlightMetricCalculator.estimate("남남향", 5, 10)).isEmpty();
        assertThat(SunlightMetricCalculator.estimate("남향", null, 10)).isEmpty();
        assertThat(SunlightMetricCalculator.estimate("남향", 5, null)).isEmpty();
        assertThat(SunlightMetricCalculator.estimate("남향", 11, 10)).isEmpty();
        assertThat(SunlightMetricCalculator.estimate("남향", 1, 0)).isEmpty();
    }
}
