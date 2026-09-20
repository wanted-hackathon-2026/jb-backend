package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RecommendedPropertyResponse(List<Item> content) {

	public record Item(
		UUID id, String name, String roadAddress, double latitude, double longitude, String thumbnailUrl,
		String propertyType, LeaseType leaseType, int deposit, int monthlyRent, BigDecimal exclusiveArea,
		Integer floor, RecommendationEvaluation evaluation
	) {

		public static Item from(Property property, String thumbnailUrl, RecommendationResult result) {
			return new Item(property.getId(), property.getName(), property.getRoadAddress(),
				property.getLat(), property.getLng(), thumbnailUrl, property.getPropertyType(),
				property.getLeaseType(), property.getDeposit(), property.getMonthlyRent(),
				property.getExclusiveArea(), property.getFloor(), RecommendationEvaluation.from(result));
		}
	}
}
