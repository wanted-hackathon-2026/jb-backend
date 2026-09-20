package com.jachwibangjeongsig.jb.recommendation.service;

import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import com.jachwibangjeongsig.jb.property.dto.PropertyDetailResponse;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository.Thumbnail;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationCreateRequest;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationEvaluation;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyDetailResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyResponse;
import com.jachwibangjeongsig.jb.recommendation.entity.Recommendation;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationResult;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationStatus;
import com.jachwibangjeongsig.jb.recommendation.exception.RecommendationNotFoundException;
import com.jachwibangjeongsig.jb.recommendation.exception.RecommendationNotReadyException;
import com.jachwibangjeongsig.jb.recommendation.exception.RecommendationWorkplaceNotFoundException;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationCriteriaRepository;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationRepository;
import com.jachwibangjeongsig.jb.recommendation.repository.RecommendationResultRepository;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecommendationService {

	private final RecommendationRepository recommendations;
	private final RecommendationCriteriaRepository criteriaRepository;
	private final RecommendationResultRepository results;
	private final WorkplaceRepository workplaces;
	private final PropertyRepository properties;
	private final PropertyImageRepository propertyImages;
	private final RecommendationProcessor processor;
	private final TransactionTemplate transactions;

	public RecommendationService(RecommendationRepository recommendations,
		RecommendationCriteriaRepository criteriaRepository, RecommendationResultRepository results,
		WorkplaceRepository workplaces, PropertyRepository properties, PropertyImageRepository propertyImages,
		RecommendationProcessor processor, PlatformTransactionManager transactionManager) {
		this.recommendations = recommendations;
		this.criteriaRepository = criteriaRepository;
		this.results = results;
		this.workplaces = workplaces;
		this.properties = properties;
		this.propertyImages = propertyImages;
		this.processor = processor;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public Recommendation request(UUID userId, RecommendationCreateRequest request) {
		Workplace workplace = workplaces.findById(request.workplaceId())
			.filter(candidate -> candidate.getUser().getId().equals(userId))
			.orElseThrow(RecommendationWorkplaceNotFoundException::new);
		Recommendation recommendation = transactions.execute(status -> {
			Recommendation saved = recommendations.saveAndFlush(
				Recommendation.pending(userId, LocalDateTime.now(ZoneOffset.UTC)));
			criteriaRepository.save(criteriaOf(saved.getId(), workplace, request));
			return saved;
		});
		// 커밋된 뒤에 넘겨야 비동기 쪽에서 방금 만든 행을 읽을 수 있다.
		processor.process(recommendation.getId());
		return recommendation;
	}

	public Recommendation status(UUID userId, UUID recommendationId) {
		return recommendations.findByIdAndUserId(recommendationId, userId)
			.orElseThrow(RecommendationNotFoundException::new);
	}

	public RecommendedPropertyResponse properties(UUID userId, UUID recommendationId) {
		requireCompleted(userId, recommendationId);
		List<RecommendationResult> ordered = results.findByRecommendationIdOrderByDisplayOrderAsc(recommendationId);
		List<UUID> propertyIds = ordered.stream().map(RecommendationResult::getPropertyId).toList();
		Map<UUID, Property> byId = new HashMap<>();
		properties.findAllById(propertyIds).forEach(property -> byId.put(property.getId(), property));
		Map<UUID, String> thumbnails = new HashMap<>();
		for (Thumbnail thumbnail : propertyImages.findThumbnails(propertyIds)) {
			thumbnails.put(thumbnail.getPropertyId(), thumbnail.getStorageKey());
		}
		List<RecommendedPropertyResponse.Item> items = new ArrayList<>();
		for (RecommendationResult result : ordered) {
			Property property = byId.get(result.getPropertyId());
			// 추천된 뒤 매물이 지워졌으면 그 줄만 조용히 건너뛴다.
			if (property != null) {
				items.add(RecommendedPropertyResponse.Item.from(
					property, imageUrl(thumbnails.get(property.getId())), result));
			}
		}
		return new RecommendedPropertyResponse(items);
	}

	public RecommendedPropertyDetailResponse property(UUID userId, UUID recommendationId, UUID propertyId) {
		requireCompleted(userId, recommendationId);
		RecommendationResult result = results.findByRecommendationIdAndPropertyId(recommendationId, propertyId)
			.orElseThrow(RecommendationNotFoundException::new);
		Property property = properties.findById(propertyId).orElseThrow(RecommendationNotFoundException::new);
		List<PropertyImage> images = propertyImages.findByPropertyIdOrderByDisplayOrderAsc(propertyId);
		return new RecommendedPropertyDetailResponse(
			PropertyDetailResponse.from(property, images, false), RecommendationEvaluation.from(result));
	}

	private void requireCompleted(UUID userId, UUID recommendationId) {
		Recommendation recommendation = status(userId, recommendationId);
		if (recommendation.getStatus() != RecommendationStatus.COMPLETED) {
			throw new RecommendationNotReadyException(recommendation.getStatus());
		}
	}

	private static RecommendationCriteria criteriaOf(UUID recommendationId, Workplace workplace,
		RecommendationCreateRequest request) {
		return RecommendationCriteria.builder()
			.recommendationId(recommendationId)
			.workplaceName(workplace.getName())
			.workplaceRoadAddress(workplace.getRoadAddress())
			.workplaceLatitude(workplace.getLat())
			.workplaceLongitude(workplace.getLng())
			.transportType(request.transportType())
			.maxCommuteMinutes(request.maxCommuteMinutes())
			.sunlightImportance(request.sunlightImportance())
			.quietnessImportance(request.quietnessImportance())
			.safetyImportance(request.safetyImportance())
			.infrastructureImportance(request.infrastructureImportance())
			.depositMin(request.depositMin())
			.depositMax(request.depositMax())
			.monthlyRentMin(request.monthlyRentMin())
			.monthlyRentMax(request.monthlyRentMax())
			.roomTypes(request.roomTypes())
			.build();
	}

	private static String imageUrl(String storageKey) {
		return storageKey == null ? null : "/api/property-images/" + storageKey;
	}
}
