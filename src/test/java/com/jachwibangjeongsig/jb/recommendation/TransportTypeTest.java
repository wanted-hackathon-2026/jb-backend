package com.jachwibangjeongsig.jb.recommendation;

import com.jachwibangjeongsig.jb.recommendation.entity.TransportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransportTypeTest {

	@ParameterizedTest
	@EnumSource(TransportType.class)
	void theRadiusForAGivenTimeIsExactlyWhatThatTimeBuysBack(TransportType transport) {
		// 반경 안의 매물이 한도를 넘는 시간으로 계산되면 후보 필터가 스스로를 배신한다.
		double radiusKm = transport.maxStraightLineKm(30);

		assertThat(transport.minutesFor(radiusKm)).isEqualTo(30);
		assertThat(transport.minutesFor(radiusKm * 1.01)).isGreaterThan(30);
	}

	@Test
	void slowerModesReachLessGroundInTheSameTime() {
		assertThat(TransportType.WALK.maxStraightLineKm(60))
			.isLessThan(TransportType.BICYCLE.maxStraightLineKm(60));
		assertThat(TransportType.BICYCLE.maxStraightLineKm(60))
			.isLessThan(TransportType.TRANSIT.maxStraightLineKm(60));
		assertThat(TransportType.TRANSIT.maxStraightLineKm(60))
			.isLessThan(TransportType.CAR.maxStraightLineKm(60));
	}

	@Test
	void aTripOfNoDistanceTakesNoTime() {
		assertThat(TransportType.TRANSIT.minutesFor(0)).isZero();
	}
}
