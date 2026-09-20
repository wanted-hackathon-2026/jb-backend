package com.jachwibangjeongsig.jb.recommendation.entity;

public enum TransportType {

	// ponytail: 실제 경로가 아니라 직선거리 기준 실효 속도다. 우회·환승·대기를 뭉뚱그려
	// 보정한 값이라 통근시간은 근사일 뿐이며, 길찾기 API 를 붙이면 이 enum 대신
	// 그 API 의 소요시간을 쓰고 여기는 폴백으로만 남기면 된다.
	WALK(3.2),
	BICYCLE(12.0),
	TRANSIT(16.0),
	CAR(24.0);

	private final double straightLineKmPerHour;

	TransportType(double straightLineKmPerHour) {
		this.straightLineKmPerHour = straightLineKmPerHour;
	}

	public double maxStraightLineKm(int minutes) {
		return straightLineKmPerHour * minutes / 60.0;
	}

	public int minutesFor(double straightLineKm) {
		return (int) Math.ceil(straightLineKm / straightLineKmPerHour * 60.0);
	}
}
