package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationStatus;
import com.jachwibangjeongsig.jb.recommendation.entity.TransportType;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecommendationHistoryResponse(
	List<Item> content, int page, int size, long totalElements, int totalPages, boolean last
) {

	public static RecommendationHistoryResponse from(Page<Recommendation> recommendations,
		Map<UUID, RecommendationCriteria> criteriaByRecommendationId) {
		return new RecommendationHistoryResponse(
			recommendations.getContent().stream()
				.map(recommendation -> Item.from(
					recommendation, criteriaByRecommendationId.get(recommendation.getId())))
				.toList(),
			recommendations.getNumber(), recommendations.getSize(), recommendations.getTotalElements(),
			recommendations.getTotalPages(), recommendations.isLast());
	}

	public record Item(
		UUID recommendationId,
		RecommendationStatus status,
		LocalDateTime requestedAt,
		LocalDateTime completedAt,
		String failureReason,
		String workplaceName,
		String workplaceRoadAddress,
		TransportType transportType,
		int maxCommuteMinutes
	) {

		private static Item from(Recommendation recommendation, RecommendationCriteria criteria) {
			return new Item(recommendation.getId(), recommendation.getStatus(), recommendation.getRequestedAt(),
				recommendation.getCompletedAt(), recommendation.getFailureReason(), criteria.getWorkplaceName(),
				criteria.getWorkplaceRoadAddress(), criteria.getTransportType(), criteria.getMaxCommuteMinutes());
		}
	}
}
