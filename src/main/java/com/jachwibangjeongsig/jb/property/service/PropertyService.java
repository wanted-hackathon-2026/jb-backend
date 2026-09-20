package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.favorite.repository.FavoriteRepository;
import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.property.dto.PropertyCreateRequest;
import com.jachwibangjeongsig.jb.property.dto.PropertyDetailResponse;
import com.jachwibangjeongsig.jb.property.dto.PropertyMapResponse;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import com.jachwibangjeongsig.jb.property.exception.PropertyAddressNotGeocodableException;
import com.jachwibangjeongsig.jb.property.exception.PropertyImageException;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository.Thumbnail;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PropertyService {

	private final PropertyRepository propertyRepository;
	private final PropertyImageRepository propertyImageRepository;
	private final FavoriteRepository favoriteRepository;
	private final GeocodingClient geocodingClient;
	private final SafetyMetricService safetyMetricService;
	private final NoiseMetricService noiseMetricService;
	private final InfrastructureMetricService infrastructureMetricService;
	private final SunlightMetricService sunlightMetricService;
	private final TransactionTemplate transactionTemplate;

	public PropertyService(PropertyRepository propertyRepository, PropertyImageRepository propertyImageRepository,
		FavoriteRepository favoriteRepository, GeocodingClient geocodingClient,
		PlatformTransactionManager transactionManager, SafetyMetricService safetyMetricService,
		NoiseMetricService noiseMetricService, InfrastructureMetricService infrastructureMetricService,
		SunlightMetricService sunlightMetricService) {
		this.propertyRepository = propertyRepository;
		this.propertyImageRepository = propertyImageRepository;
		this.favoriteRepository = favoriteRepository;
		this.geocodingClient = geocodingClient;
		this.safetyMetricService = safetyMetricService;
		this.noiseMetricService = noiseMetricService;
		this.infrastructureMetricService = infrastructureMetricService;
		this.sunlightMetricService = sunlightMetricService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public Property create(PropertyCreateRequest request) {
		// Do not hold a database transaction open while waiting for VWorld.
		Coordinates coordinates = geocodingClient.locate(request.roadAddress())
			.orElseThrow(PropertyAddressNotGeocodableException::new);
		Property property = transactionTemplate.execute(status -> propertyRepository.saveAndFlush(Property.builder()
			.name(request.name()).address(request.address()).roadAddress(request.roadAddress())
			.sggCode(request.sggCode()).umdName(request.umdName())
			.lat(coordinates.lat()).lng(coordinates.lng()).propertyType(request.propertyType())
			.leaseType(request.leaseType())
			.deposit(request.deposit()).monthlyRent(request.monthlyRent())
			.exclusiveArea(request.exclusiveArea()).supplyArea(request.supplyArea())
			.floor(request.floor()).bathroomCount(request.bathroomCount()).totalFloors(request.totalFloors())
			.buildYear(request.buildYear()).direction(request.direction()).description(request.description())
			.build()));
		safetyMetricService.collect(property);
		noiseMetricService.collect(property);
		infrastructureMetricService.collect(property);
		sunlightMetricService.collect(property);
		return property;
	}

	public PropertyMapResponse findWithinMapBounds(double minLat, double maxLat, double minLng, double maxLng,
		int limit, UUID viewerUserId) {
		validateBounds(minLat, maxLat, minLng, maxLng);
		List<Property> properties = propertyRepository.findByLatBetweenAndLngBetweenOrderByCreatedAtDesc(
			minLat, maxLat, minLng, maxLng, PageRequest.of(0, limit));
		List<UUID> propertyIds = properties.stream().map(Property::getId).toList();
		Map<UUID, String> thumbnails = new HashMap<>();
		for (Thumbnail thumbnail : propertyImageRepository.findThumbnails(propertyIds)) {
			thumbnails.put(thumbnail.getPropertyId(), thumbnail.getStorageKey());
		}
		Set<UUID> favorited = viewerUserId == null ? Set.of()
			: new HashSet<>(favoriteRepository.findFavoritedPropertyIds(viewerUserId, propertyIds));
		return new PropertyMapResponse(properties.stream()
			.map(property -> PropertyMapResponse.Item.from(property, imageUrl(thumbnails.get(property.getId())),
				favorited.contains(property.getId())))
			.toList());
	}

	public PropertyDetailResponse detail(UUID propertyId, UUID viewerUserId) {
		Property property = propertyRepository.findById(propertyId).orElseThrow(
			() -> new PropertyImageException(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "매물을 찾을 수 없습니다."));
		List<PropertyImage> images = propertyImageRepository.findByPropertyIdOrderByDisplayOrderAsc(propertyId);
		boolean favorite = viewerUserId != null
			&& favoriteRepository.existsByUserIdAndPropertyId(viewerUserId, propertyId);
		return PropertyDetailResponse.from(property, images, favorite);
	}

	private static void validateBounds(double minLat, double maxLat, double minLng, double maxLng) {
		if (!inLatRange(minLat) || !inLatRange(maxLat) || !inLngRange(minLng) || !inLngRange(maxLng)
			|| minLat > maxLat || minLng > maxLng) {
			throw new PropertyImageException(HttpStatus.BAD_REQUEST, "INVALID_MAP_BOUNDS", "지도 영역 좌표가 올바르지 않습니다.");
		}
	}

	private static boolean inLatRange(double lat) {
		return lat >= -90 && lat <= 90;
	}

	private static boolean inLngRange(double lng) {
		return lng >= -180 && lng <= 180;
	}

	private static String imageUrl(String storageKey) {
		return storageKey == null ? null : "/api/property-images/" + storageKey;
	}
}
