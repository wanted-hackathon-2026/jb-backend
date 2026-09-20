package com.jachwibangjeongsig.jb.recommendation.service;

import com.jachwibangjeongsig.jb.global.llm.LlmClient;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyFeature;
import com.jachwibangjeongsig.jb.property.repository.PropertyFeatureRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator;
import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationResult;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationCriteriaRepository;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationRepository;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 추천 한 건의 실제 처리. 요청 스레드가 아니라 비동기 실행기 위에서 돈다. */
@Component
public class RecommendationProcessor {

	private static final Logger log = LoggerFactory.getLogger(RecommendationProcessor.class);

	/** LLM 에 한 번에 넣는 후보 수 상한. 토큰 비용과 응답 시간을 묶어 두기 위한 값이다. */
	private static final int MAX_CANDIDATES = 15;
	/** 설명은 프롬프트에서 길이를 차지하기만 해서 앞부분만 넣는다. */
	private static final int MAX_DESCRIPTION = 200;
	private static final double KM_PER_LATITUDE_DEGREE = 111.0;

	private final RecommendationRepository recommendations;
	private final RecommendationCriteriaRepository criteriaRepository;
	private final RecommendationResultRepository results;
	private final PropertyRepository properties;
	private final PropertyFeatureRepository features;
	private final LlmClient llmClient;
	private final TransactionTemplate transactions;

	public RecommendationProcessor(RecommendationRepository recommendations,
		RecommendationCriteriaRepository criteriaRepository, RecommendationResultRepository results,
		PropertyRepository properties, PropertyFeatureRepository features, LlmClient llmClient,
		PlatformTransactionManager transactionManager) {
		this.recommendations = recommendations;
		this.criteriaRepository = criteriaRepository;
		this.results = results;
		this.properties = properties;
		this.features = features;
		this.llmClient = llmClient;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** LLM 이 채워야 하는 응답 형태. 최상위가 객체여야 해서 목록을 한 번 감싼다. */
	public record Evaluations(List<Evaluation> evaluations) {
	}

	public record Evaluation(int candidateNumber, int sunlightScore, int quietnessScore, int safetyScore,
		int infrastructureScore, int commuteScore, int totalScore, String summary) {
	}

	private record Candidate(Property property, int commuteMinutes) {
	}

	@Async
	public void process(UUID recommendationId) {
		try {
			RecommendationCriteria criteria = markProcessing(recommendationId);
			List<Candidate> candidates = findCandidates(criteria);
			if (candidates.isEmpty()) {
				// 조건에 맞는 매물이 없는 것은 실패가 아니다. 빈 결과로 완료 처리한다.
				complete(recommendationId, List.of());
				return;
			}
			complete(recommendationId, evaluate(recommendationId, criteria, candidates));
		} catch (RuntimeException failure) {
			log.warn("Recommendation failed: id={}, cause={}", recommendationId, failure.toString());
			fail(recommendationId, failure);
		}
	}

	private RecommendationCriteria markProcessing(UUID recommendationId) {
		return transactions.execute(status -> {
			Recommendation recommendation = recommendations.findById(recommendationId)
				.orElseThrow(() -> new IllegalStateException("Recommendation disappeared: " + recommendationId));
			recommendation.markProcessing(LocalDateTime.now(ZoneOffset.UTC));
			recommendations.save(recommendation);
			return criteriaRepository.findByRecommendationId(recommendationId)
				.orElseThrow(() -> new IllegalStateException("Criteria missing: " + recommendationId));
		});
	}

	private List<Candidate> findCandidates(RecommendationCriteria criteria) {
		double radiusKm = criteria.getTransportType().maxStraightLineKm(criteria.getMaxCommuteMinutes());
		double latDelta = radiusKm / KM_PER_LATITUDE_DEGREE;
		double lngDelta = radiusKm
			/ (KM_PER_LATITUDE_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(criteria.getWorkplaceLatitude()))));
		// 근무지를 시설 한 곳으로 두고 기존 Haversine 구현을 그대로 쓴다.
		SafetyMetricCalculator.Facility workplace = new SafetyMetricCalculator.Facility(
			criteria.getWorkplaceLatitude(), criteria.getWorkplaceLongitude(), 0);
		return properties.findRecommendationCandidates(
				criteria.getWorkplaceLatitude() - latDelta, criteria.getWorkplaceLatitude() + latDelta,
				criteria.getWorkplaceLongitude() - lngDelta, criteria.getWorkplaceLongitude() + lngDelta,
				criteria.getDepositMin(), criteria.getDepositMax(),
				criteria.getMonthlyRentMin(), criteria.getMonthlyRentMax(), criteria.roomTypeList())
			.stream()
			.map(property -> new Candidate(property, criteria.getTransportType().minutesFor(
				SafetyMetricCalculator.distanceMeters(property.getLat(), property.getLng(), workplace) / 1000.0)))
			// 사각형은 반경의 외접 박스라 모서리 쪽 매물이 통근 한도를 넘을 수 있다.
			.filter(candidate -> candidate.commuteMinutes() <= criteria.getMaxCommuteMinutes())
			.sorted(Comparator.comparingInt(Candidate::commuteMinutes))
			.limit(MAX_CANDIDATES)
			.toList();
	}

	private List<RecommendationResult> evaluate(UUID recommendationId, RecommendationCriteria criteria,
		List<Candidate> candidates) {
		Evaluations evaluations = llmClient.complete(
			RecommendationPrompt.system(), RecommendationPrompt.user(criteria, candidateLines(candidates)),
			Evaluations.class);
		return toResults(recommendationId, candidates, evaluations);
	}

	private List<String> candidateLines(List<Candidate> candidates) {
		Map<UUID, List<PropertyFeature>> byProperty = new LinkedHashMap<>();
		for (PropertyFeature feature : features.findByPropertyIdIn(
			candidates.stream().map(candidate -> candidate.property().getId()).toList())) {
			byProperty.computeIfAbsent(feature.getPropertyId(), key -> new ArrayList<>()).add(feature);
		}
		List<String> lines = new ArrayList<>();
		for (int index = 0; index < candidates.size(); index++) {
			Candidate candidate = candidates.get(index);
			lines.add(RecommendationPrompt.candidate(index + 1, candidate.property(), candidate.commuteMinutes(),
				byProperty.getOrDefault(candidate.property().getId(), List.of()), MAX_DESCRIPTION));
		}
		return lines;
	}

	/** 모델 출력은 신뢰 경계 밖이다. 번호와 점수를 검증하고 못 믿을 항목은 버린다. */
	private List<RecommendationResult> toResults(UUID recommendationId, List<Candidate> candidates,
		Evaluations evaluations) {
		if (evaluations == null || evaluations.evaluations() == null) {
			throw new IllegalStateException("LLM returned no evaluations");
		}
		Set<Integer> seen = new HashSet<>();
		List<Evaluation> usable = new ArrayList<>();
		for (Evaluation evaluation : evaluations.evaluations()) {
			if (evaluation == null || evaluation.candidateNumber() < 1
				|| evaluation.candidateNumber() > candidates.size()
				|| evaluation.summary() == null || evaluation.summary().isBlank()
				|| !seen.add(evaluation.candidateNumber())) {
				continue;
			}
			usable.add(evaluation);
		}
		if (usable.isEmpty()) {
			throw new IllegalStateException("LLM returned no usable evaluation");
		}
		usable.sort(Comparator.comparingInt(Evaluation::totalScore).reversed());
		List<RecommendationResult> saved = new ArrayList<>();
		for (int index = 0; index < usable.size(); index++) {
			Evaluation evaluation = usable.get(index);
			Candidate candidate = candidates.get(evaluation.candidateNumber() - 1);
			saved.add(RecommendationResult.builder()
				.recommendationId(recommendationId)
				.propertyId(candidate.property().getId())
				.displayOrder(index + 1)
				.commuteMinutes(candidate.commuteMinutes())
				.totalScore(clampScore(evaluation.totalScore()))
				.sunlightScore(clampScore(evaluation.sunlightScore()))
				.quietnessScore(clampScore(evaluation.quietnessScore()))
				.safetyScore(clampScore(evaluation.safetyScore()))
				.infrastructureScore(clampScore(evaluation.infrastructureScore()))
				.commuteScore(clampScore(evaluation.commuteScore()))
				.summary(evaluation.summary())
				.build());
		}
		return saved;
	}

	private static int clampScore(int score) {
		return Math.clamp(score, 0, 100);
	}

	private void complete(UUID recommendationId, List<RecommendationResult> evaluated) {
		transactions.executeWithoutResult(status -> {
			results.saveAll(evaluated);
			Recommendation recommendation = recommendations.findById(recommendationId).orElseThrow();
			recommendation.markCompleted(LocalDateTime.now(ZoneOffset.UTC));
			recommendations.save(recommendation);
		});
	}

	private void fail(UUID recommendationId, RuntimeException failure) {
		try {
			transactions.executeWithoutResult(status -> recommendations.findById(recommendationId)
				.ifPresent(recommendation -> {
					recommendation.markFailed(failure.getClass().getSimpleName() + ": " + failure.getMessage(),
						LocalDateTime.now(ZoneOffset.UTC));
					recommendations.save(recommendation);
				}));
		} catch (RuntimeException ignored) {
			// 실패를 기록하다 또 실패해도 남길 곳이 없다. 로그는 호출부에서 이미 찍었다.
			log.warn("Could not record recommendation failure: id={}", recommendationId);
		}
	}
}
