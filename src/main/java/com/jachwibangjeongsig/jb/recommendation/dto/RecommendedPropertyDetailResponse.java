package com.jachwibangjeongsig.jb.recommendation.dto;

import com.jachwibangjeongsig.jb.property.dto.PropertyDetailResponse;

/** 매물 상세는 기존 응답을 그대로 쓰고, 이 추천에서의 평가만 덧붙인다. */
public record RecommendedPropertyDetailResponse(
	PropertyDetailResponse property, RecommendationEvaluation evaluation
) {
}
