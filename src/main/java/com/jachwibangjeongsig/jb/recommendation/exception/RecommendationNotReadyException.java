package com.jachwibangjeongsig.jb.recommendation.exception;

import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationStatus;

/** 아직 끝나지 않았거나 실패한 추천의 결과를 조회했을 때. */
public class RecommendationNotReadyException extends RuntimeException {

	private final RecommendationStatus status;

	public RecommendationNotReadyException(RecommendationStatus status) {
		super("추천이 아직 완료되지 않았습니다. 현재 상태: " + status);
		this.status = status;
	}

	public RecommendationStatus status() {
		return status;
	}
}
