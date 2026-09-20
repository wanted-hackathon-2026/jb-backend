package com.jachwibangjeongsig.jb.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** 한 매물에 대한 LLM 평가. display_order 오름차순이 추천 순위다. */
@Getter
@Entity
@Table(name = "recommendation_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationResult {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "recommendation_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID recommendationId;

	@Column(name = "property_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID propertyId;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "commute_minutes", nullable = false)
	private int commuteMinutes;

	@Column(name = "total_score", nullable = false)
	private int totalScore;

	@Column(name = "sunlight_score", nullable = false)
	private int sunlightScore;

	@Column(name = "quietness_score", nullable = false)
	private int quietnessScore;

	@Column(name = "safety_score", nullable = false)
	private int safetyScore;

	@Column(name = "infrastructure_score", nullable = false)
	private int infrastructureScore;

	@Column(name = "commute_score", nullable = false)
	private int commuteScore;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String summary;

	@Builder
	private RecommendationResult(UUID recommendationId, UUID propertyId, int displayOrder, int commuteMinutes,
		int totalScore, int sunlightScore, int quietnessScore, int safetyScore, int infrastructureScore,
		int commuteScore, String summary) {
		this.recommendationId = recommendationId;
		this.propertyId = propertyId;
		this.displayOrder = displayOrder;
		this.commuteMinutes = commuteMinutes;
		this.totalScore = totalScore;
		this.sunlightScore = sunlightScore;
		this.quietnessScore = quietnessScore;
		this.safetyScore = safetyScore;
		this.infrastructureScore = infrastructureScore;
		this.commuteScore = commuteScore;
		this.summary = summary;
	}
}
