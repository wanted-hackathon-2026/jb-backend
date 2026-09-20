package com.jachwibangjeongsig.jb.recommendation.repository;

import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

	/** 남의 추천을 조회하면 존재 자체를 숨기려고 404 로 떨어뜨리므로 소유자까지 조건에 넣는다. */
	Optional<Recommendation> findByIdAndUserId(UUID id, UUID userId);
}
