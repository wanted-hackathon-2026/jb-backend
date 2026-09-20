package com.jachwibangjeongsig.jb.property.dto;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyResponse(
	UUID id, String name, String address, String roadAddress, String sggCode, String umdName,
	double latitude, double longitude, String propertyType, LeaseType leaseType, int deposit, int monthlyRent,
	BigDecimal exclusiveArea, BigDecimal supplyArea, Integer floor, Integer bathroomCount, Integer totalFloors,
	Integer buildYear, String direction, String description, LocalDateTime createdAt, LocalDateTime updatedAt
) {
	public static PropertyResponse from(Property property) {
		return new PropertyResponse(property.getId(), property.getName(), property.getAddress(),
			property.getRoadAddress(), property.getSggCode(), property.getUmdName(),
			property.getLat(), property.getLng(), property.getPropertyType(),
			property.getLeaseType(),
			property.getDeposit(), property.getMonthlyRent(), property.getExclusiveArea(), property.getSupplyArea(),
			property.getFloor(), property.getBathroomCount(), property.getTotalFloors(), property.getBuildYear(),
			property.getDirection(), property.getDescription(), property.getCreatedAt(), property.getUpdatedAt());
	}
}
