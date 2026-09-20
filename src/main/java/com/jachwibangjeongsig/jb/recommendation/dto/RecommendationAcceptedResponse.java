package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationStatus;

import java.util.UUID;

public record RecommendationAcceptedResponse(UUID recommendationId, RecommendationStatus status) {

	public static RecommendationAcceptedResponse from(Recommendation recommendation) {
		return new RecommendationAcceptedResponse(recommendation.getId(), recommendation.getStatus());
	}
}
