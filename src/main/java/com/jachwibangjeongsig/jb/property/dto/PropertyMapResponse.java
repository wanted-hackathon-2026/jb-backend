package com.jachwibangjeongsig.jb.property.dto;

import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PropertyMapResponse(List<Item> properties) {

	public record Item(UUID id, double latitude, double longitude, String thumbnailUrl, String name,
		String propertyType, LeaseType leaseType, int deposit, int monthlyRent, BigDecimal exclusiveArea,
		Integer floor, String address, boolean favorite) {
		public static Item from(Property property, String thumbnailUrl, boolean favorite) {
			return new Item(property.getId(), property.getLat(), property.getLng(), thumbnailUrl,
				property.getName(), property.getPropertyType(), property.getLeaseType(), property.getDeposit(),
				property.getMonthlyRent(), property.getExclusiveArea(), property.getFloor(), property.getAddress(),
				favorite);
		}
	}
}
