package com.jachwibangjeongsig.jb.recommendation.repository;

import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, UUID> {

	List<RecommendationResult> findByRecommendationIdOrderByDisplayOrderAsc(UUID recommendationId);

	Optional<RecommendationResult> findByRecommendationIdAndPropertyId(UUID recommendationId, UUID propertyId);
}
