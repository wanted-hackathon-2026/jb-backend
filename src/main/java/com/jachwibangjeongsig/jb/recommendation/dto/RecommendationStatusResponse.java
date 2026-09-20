package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecommendationStatusResponse(
	UUID recommendationId, RecommendationStatus status, LocalDateTime requestedAt,
	LocalDateTime startedAt, LocalDateTime completedAt, String failureReason
) {

	public static RecommendationStatusResponse from(Recommendation recommendation) {
		return new RecommendationStatusResponse(recommendation.getId(), recommendation.getStatus(),
			recommendation.getRequestedAt(), recommendation.getStartedAt(), recommendation.getCompletedAt(),
			recommendation.getFailureReason());
	}
}
