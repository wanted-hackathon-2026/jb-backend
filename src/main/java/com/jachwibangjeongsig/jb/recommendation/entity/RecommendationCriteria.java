package com.jachwibangjeongsig.jb.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** 추천 요청 시점의 조건 스냅샷. 근무지나 선호도가 나중에 바뀌어도 이 추천의 근거는 보존된다. */
@Getter
@Entity
@Table(name = "recommendation_criteria")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationCriteria {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "recommendation_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID recommendationId;

	@Column(name = "workplace_name", nullable = false, length = 50)
	private String workplaceName;

	@Column(name = "workplace_road_address", nullable = false, length = 255)
	private String workplaceRoadAddress;

	@Column(name = "workplace_latitude", nullable = false)
	private double workplaceLatitude;

	@Column(name = "workplace_longitude", nullable = false)
	private double workplaceLongitude;

	@Enumerated(EnumType.STRING)
	@Column(name = "transport_type", nullable = false, length = 20)
	private TransportType transportType;

	@Column(name = "max_commute_minutes", nullable = false)
	private int maxCommuteMinutes;

	@Column(name = "sunlight_importance", nullable = false)
	private int sunlightImportance;

	@Column(name = "quietness_importance", nullable = false)
	private int quietnessImportance;

	@Column(name = "safety_importance", nullable = false)
	private int safetyImportance;

	@Column(name = "infrastructure_importance", nullable = false)
	private int infrastructureImportance;

	@Column(name = "deposit_min", nullable = false)
	private int depositMin;

	@Column(name = "deposit_max", nullable = false)
	private int depositMax;

	@Column(name = "monthly_rent_min", nullable = false)
	private int monthlyRentMin;

	@Column(name = "monthly_rent_max", nullable = false)
	private int monthlyRentMax;

	/** 쉼표로 이어 붙인 매물 유형. 한 컬럼에 담으라고 V2 가 VARCHAR 로 잡아 두었다. */
	@Column(name = "room_types", nullable = false, length = 255)
	private String roomTypes;

	@Builder
	private RecommendationCriteria(UUID recommendationId, String workplaceName, String workplaceRoadAddress,
		double workplaceLatitude, double workplaceLongitude, TransportType transportType, int maxCommuteMinutes,
		int sunlightImportance, int quietnessImportance, int safetyImportance, int infrastructureImportance,
		int depositMin, int depositMax, int monthlyRentMin, int monthlyRentMax, List<String> roomTypes) {
		this.recommendationId = recommendationId;
		this.workplaceName = workplaceName;
		this.workplaceRoadAddress = workplaceRoadAddress;
		this.workplaceLatitude = workplaceLatitude;
		this.workplaceLongitude = workplaceLongitude;
		this.transportType = transportType;
		this.maxCommuteMinutes = maxCommuteMinutes;
		this.sunlightImportance = sunlightImportance;
		this.quietnessImportance = quietnessImportance;
		this.safetyImportance = safetyImportance;
		this.infrastructureImportance = infrastructureImportance;
		this.depositMin = depositMin;
		this.depositMax = depositMax;
		this.monthlyRentMin = monthlyRentMin;
		this.monthlyRentMax = monthlyRentMax;
		this.roomTypes = String.join(",", roomTypes);
	}

	public List<String> roomTypeList() {
		return Arrays.asList(roomTypes.split(","));
	}
}
