package com.jachwibangjeongsig.jb.recommendation.service;

import java.util.UUID;

/**
 * 추천의 주인. 회원이거나 비로그인 세션이거나 둘 중 하나다
 * (스키마의 chk_recommendation_owner 와 같은 규칙).
 */
public record RecommendationOwner(UUID userId, UUID clientSessionId) {

	public RecommendationOwner {
		if ((userId == null) == (clientSessionId == null)) {
			throw new IllegalArgumentException("Exactly one of userId and clientSessionId must be set");
		}
	}

	public static RecommendationOwner ofUser(UUID userId) {
		return new RecommendationOwner(userId, null);
	}

	public static RecommendationOwner ofClientSession(UUID clientSessionId) {
		return new RecommendationOwner(null, clientSessionId);
	}

	public boolean isLoggedIn() {
		return userId != null;
	}
}
