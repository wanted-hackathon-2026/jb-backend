package com.jachwibangjeongsig.jb.workplace.dto;

import com.jachwibangjeongsig.jb.workplace.entity.Workplace;

import java.util.UUID;

public record WorkplaceResponse(
	UUID id,
	String name,
	String roadAddress,
	double lat,
	double lng
) {

	public static WorkplaceResponse from(Workplace workplace) {
		return new WorkplaceResponse(
			workplace.getId(),
			workplace.getName(),
			workplace.getRoadAddress(),
			workplace.getLat(),
			workplace.getLng()
		);
	}
}
