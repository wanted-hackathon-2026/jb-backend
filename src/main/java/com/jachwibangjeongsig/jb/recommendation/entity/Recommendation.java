package com.jachwibangjeongsig.jb.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "recommendation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

	// 실패 사유는 상태 조회로 그대로 나가므로 컬럼 길이를 넘기지 않게 잘라 담는다.
	private static final int MAX_FAILURE_REASON = 255;

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	// 스키마의 chk_recommendation_owner 가 둘 중 정확히 하나만 채워지도록 막는다.
	@Column(name = "user_id", columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "client_session_id", columnDefinition = "BINARY(16)")
	private UUID clientSessionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecommendationStatus status;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "failure_reason", length = MAX_FAILURE_REASON)
	private String failureReason;

	/** 저장 시점이 아니라 생성 시점에 터지도록 XOR 를 여기서도 막는다. */
	public static Recommendation pending(UUID userId, UUID clientSessionId, LocalDateTime requestedAt) {
		if ((userId == null) == (clientSessionId == null)) {
			throw new IllegalArgumentException("Exactly one of userId and clientSessionId must be set");
		}
		Recommendation recommendation = new Recommendation();
		recommendation.userId = userId;
		recommendation.clientSessionId = clientSessionId;
		recommendation.status = RecommendationStatus.PENDING;
		recommendation.requestedAt = requestedAt;
		return recommendation;
	}

	public void markProcessing(LocalDateTime startedAt) {
		this.status = RecommendationStatus.PROCESSING;
		this.startedAt = startedAt;
	}

	public void markCompleted(LocalDateTime completedAt) {
		this.status = RecommendationStatus.COMPLETED;
		this.completedAt = completedAt;
	}

	public void markFailed(String reason, LocalDateTime completedAt) {
		this.status = RecommendationStatus.FAILED;
		this.completedAt = completedAt;
		this.failureReason = reason == null || reason.length() <= MAX_FAILURE_REASON
			? reason : reason.substring(0, MAX_FAILURE_REASON);
	}
}
