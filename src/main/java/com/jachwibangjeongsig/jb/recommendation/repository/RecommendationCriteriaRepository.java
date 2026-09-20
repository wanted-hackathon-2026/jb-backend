package com.jachwibangjeongsig.jb.recommendation.repository;

import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationCriteriaRepository extends JpaRepository<RecommendationCriteria, UUID> {

	Optional<RecommendationCriteria> findByRecommendationId(UUID recommendationId);
}
