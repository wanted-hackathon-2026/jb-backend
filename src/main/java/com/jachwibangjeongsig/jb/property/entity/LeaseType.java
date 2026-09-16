package com.jachwibangjeongsig.jb.property.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LeaseType {
	JEONSE,
	MONTHLY;

	@JsonCreator
	public static LeaseType fromJson(String value) {
		return value == null ? null : LeaseType.valueOf(value);
	}
}
