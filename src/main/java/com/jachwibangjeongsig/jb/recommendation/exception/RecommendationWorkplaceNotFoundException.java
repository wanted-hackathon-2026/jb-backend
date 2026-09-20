package com.jachwibangjeongsig.jb.recommendation.exception;

/** 남의 근무지를 지정한 경우도 존재를 숨기려고 같은 예외로 처리한다. */
public class RecommendationWorkplaceNotFoundException extends RuntimeException {

	public RecommendationWorkplaceNotFoundException() {
		super("근무지를 찾을 수 없습니다.");
	}
}
