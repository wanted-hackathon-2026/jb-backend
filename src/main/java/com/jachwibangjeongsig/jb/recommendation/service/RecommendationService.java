package com.jachwibangjeongsig.jb.recommendation.service;

import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import com.jachwibangjeongsig.jb.property.dto.PropertyDetailResponse;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository.Thumbnail;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationCreateRequest;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationEvaluation;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationHistoryResponse;
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
import com.jachwibangjeongsig.jb.workplace.exception.AddressNotGeocodableException;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
	private final GeocodingClient geocodingClient;
	private final RecommendationProcessor processor;
	private final TransactionTemplate transactions;

	public RecommendationService(RecommendationRepository recommendations,
		RecommendationCriteriaRepository criteriaRepository, RecommendationResultRepository results,
		WorkplaceRepository workplaces, PropertyRepository properties, PropertyImageRepository propertyImages,
		GeocodingClient geocodingClient, RecommendationProcessor processor,
		PlatformTransactionManager transactionManager) {
		this.recommendations = recommendations;
		this.criteriaRepository = criteriaRepository;
		this.results = results;
		this.workplaces = workplaces;
		this.properties = properties;
		this.propertyImages = propertyImages;
		this.geocodingClient = geocodingClient;
		this.processor = processor;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public Recommendation request(RecommendationOwner owner, RecommendationCreateRequest request) {
		// 외부 호출(VWorld)을 트랜잭션 안에서 기다리지 않는다.
		WorkplaceSnapshot snapshot = snapshotOf(owner, request);
		Recommendation recommendation = transactions.execute(status -> {
			Recommendation saved = recommendations.saveAndFlush(Recommendation.pending(
				owner.userId(), owner.clientSessionId(), LocalDateTime.now(ZoneOffset.UTC)));
			criteriaRepository.save(criteriaOf(saved.getId(), snapshot, request));
			return saved;
		});
		// 커밋된 뒤에 넘겨야 비동기 쪽에서 방금 만든 행을 읽을 수 있다.
		processor.process(recommendation.getId());
		return recommendation;
	}

	public Recommendation status(RecommendationOwner owner, UUID recommendationId) {
		return (owner.isLoggedIn()
			? recommendations.findByIdAndUserId(recommendationId, owner.userId())
			: recommendations.findByIdAndClientSessionId(recommendationId, owner.clientSessionId()))
			.orElseThrow(RecommendationNotFoundException::new);
	}

	public RecommendationHistoryResponse history(RecommendationOwner owner, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size,
			Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
		Page<Recommendation> history = owner.isLoggedIn()
			? recommendations.findAllByUserId(owner.userId(), pageable)
			: recommendations.findAllByClientSessionId(owner.clientSessionId(), pageable);
		List<UUID> recommendationIds = history.getContent().stream().map(Recommendation::getId).toList();
		Map<UUID, RecommendationCriteria> criteriaByRecommendationId = new HashMap<>();
		if (!recommendationIds.isEmpty()) {
			criteriaRepository.findByRecommendationIdIn(recommendationIds)
				.forEach(criteria -> criteriaByRecommendationId.put(criteria.getRecommendationId(), criteria));
		}
		return RecommendationHistoryResponse.from(history, criteriaByRecommendationId);
	}

	public RecommendedPropertyResponse properties(RecommendationOwner owner, UUID recommendationId) {
		requireCompleted(owner, recommendationId);
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

	public RecommendedPropertyDetailResponse property(RecommendationOwner owner, UUID recommendationId,
		UUID propertyId) {
		requireCompleted(owner, recommendationId);
		RecommendationResult result = results.findByRecommendationIdAndPropertyId(recommendationId, propertyId)
			.orElseThrow(RecommendationNotFoundException::new);
		Property property = properties.findById(propertyId).orElseThrow(RecommendationNotFoundException::new);
		List<PropertyImage> images = propertyImages.findByPropertyIdOrderByDisplayOrderAsc(propertyId);
		return new RecommendedPropertyDetailResponse(
			PropertyDetailResponse.from(property, images, false), RecommendationEvaluation.from(result));
	}

	private void requireCompleted(RecommendationOwner owner, UUID recommendationId) {
		Recommendation recommendation = status(owner, recommendationId);
		if (recommendation.getStatus() != RecommendationStatus.COMPLETED) {
			throw new RecommendationNotReadyException(recommendation.getStatus());
		}
	}

	/**
	 * 저장된 거점을 고르면 그 값을, 주소를 직접 넣으면 서버가 지오코딩한 좌표를 쓴다.
	 * 좌표를 클라이언트가 보내게 두지 않는 것은 매물·거점 등록과 같은 규칙이다.
	 */
	private WorkplaceSnapshot snapshotOf(RecommendationOwner owner, RecommendationCreateRequest request) {
		if (request.workplaceId() != null) {
			// 비로그인 사용자는 거점을 가질 수 없다. 남의 거점처럼 존재를 감춰 404 로 맞춘다.
			if (!owner.isLoggedIn()) {
				throw new RecommendationWorkplaceNotFoundException();
			}
			Workplace workplace = workplaces.findById(request.workplaceId())
				.filter(candidate -> candidate.getUser().getId().equals(owner.userId()))
				.orElseThrow(RecommendationWorkplaceNotFoundException::new);
			return new WorkplaceSnapshot(workplace.getName(), workplace.getRoadAddress(),
				workplace.getLat(), workplace.getLng());
		}
		Coordinates coordinates = geocodingClient.locate(request.workplace().roadAddress())
			.orElseThrow(AddressNotGeocodableException::new);
		return new WorkplaceSnapshot(request.workplace().name(), request.workplace().roadAddress(),
			coordinates.lat(), coordinates.lng());
	}

	/** 요청 시점의 거점. 저장된 거점에서 왔든 주소에서 왔든 이후 처리는 같다. */
	private record WorkplaceSnapshot(String name, String roadAddress, double latitude, double longitude) {
	}

	private static RecommendationCriteria criteriaOf(UUID recommendationId, WorkplaceSnapshot workplace,
		RecommendationCreateRequest request) {
		return RecommendationCriteria.builder()
			.recommendationId(recommendationId)
			.workplaceName(workplace.name())
			.workplaceRoadAddress(workplace.roadAddress())
			.workplaceLatitude(workplace.latitude())
			.workplaceLongitude(workplace.longitude())
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
