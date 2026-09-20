package com.jachwibangjeongsig.jb.property.dto;

import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PropertyDetailResponse(
	UUID id, String name, String address, String roadAddress, double latitude, double longitude,
	String propertyType, LeaseType leaseType, int deposit, int monthlyRent, BigDecimal exclusiveArea,
	BigDecimal supplyArea, Integer floor, Integer bathroomCount, Integer totalFloors, Integer buildYear,
	String direction, String description, List<PropertyImageResponse.Image> images, boolean favorite
) {
	public static PropertyDetailResponse from(Property property, List<PropertyImage> images, boolean favorite) {
		return new PropertyDetailResponse(property.getId(), property.getName(), property.getAddress(),
			property.getRoadAddress(), property.getLat(), property.getLng(), property.getPropertyType(),
			property.getLeaseType(), property.getDeposit(), property.getMonthlyRent(), property.getExclusiveArea(),
			property.getSupplyArea(), property.getFloor(), property.getBathroomCount(), property.getTotalFloors(),
			property.getBuildYear(), property.getDirection(), property.getDescription(),
			images.stream().map(PropertyImageResponse.Image::from).toList(), favorite);
	}
}
