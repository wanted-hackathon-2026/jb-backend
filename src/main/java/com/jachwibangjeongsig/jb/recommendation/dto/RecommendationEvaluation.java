package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationResult;

/** 한 매물에 대한 LLM 평가. rank 는 1 부터 시작하는 추천 순위다. */
public record RecommendationEvaluation(
	int rank, int commuteMinutes, int totalScore, int sunlightScore, int quietnessScore,
	int safetyScore, int infrastructureScore, int commuteScore, String summary
) {

	public static RecommendationEvaluation from(RecommendationResult result) {
		return new RecommendationEvaluation(result.getDisplayOrder(), result.getCommuteMinutes(),
			result.getTotalScore(), result.getSunlightScore(), result.getQuietnessScore(),
			result.getSafetyScore(), result.getInfrastructureScore(), result.getCommuteScore(),
			result.getSummary());
	}
}
