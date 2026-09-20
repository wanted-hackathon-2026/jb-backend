package com.jachwibangjeongsig.jb.recommendation.service;

import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.recommendation.entity.RecommendationCriteria;
import com.jachwibangjeongsig.jb.recommendation.entity.TransportType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPromptTest {

	private static final int MAX_DESCRIPTION = 20;

	private static Property property(String description) {
		return Property.builder()
			.name("역삼 원룸").address("서울 강남구 역삼동 1").roadAddress("서울 강남구 테헤란로 1")
			.sggCode("11680").umdName("역삼동").lat(37.5).lng(127.0)
			.propertyType("원룸").leaseType(LeaseType.MONTHLY).deposit(1000).monthlyRent(60)
			.exclusiveArea(new BigDecimal("23.50")).floor(3).totalFloors(10).buildYear(2020)
			.direction("남향").description(description)
			.build();
	}

	private static RecommendationCriteria criteria() {
		return RecommendationCriteria.builder()
			.recommendationId(UUID.randomUUID())
			.workplaceName("본사").workplaceRoadAddress("서울 강남구 강남대로 1")
			.workplaceLatitude(37.5).workplaceLongitude(127.0)
			.transportType(TransportType.TRANSIT).maxCommuteMinutes(30)
			.sunlightImportance(5).quietnessImportance(4).safetyImportance(3).infrastructureImportance(2)
			.depositMin(0).depositMax(2000).monthlyRentMin(0).monthlyRentMax(80)
			.roomTypes(List.of("원룸", "오피스텔"))
			.build();
	}

	@Test
	void theUserPromptCarriesEveryConditionTheModelIsAskedToWeigh() {
		String prompt = RecommendationPrompt.user(criteria(), List.of("후보 1: 역삼 원룸"));

		assertThat(prompt)
			.contains("본사", "서울 강남구 강남대로 1")
			.contains("TRANSIT", "30분")
			.contains("채광 5", "조용함 4", "치안 3", "인프라 2")
			.contains("0~2000만원", "0~80만원")
			.contains("원룸, 오피스텔")
			.contains("후보 1: 역삼 원룸");
	}

	@Test
	void aCandidateLineNumbersItselfAndStatesTheEstimatedCommute() {
		String line = RecommendationPrompt.candidate(3, property(null), 17, List.of(), MAX_DESCRIPTION);

		assertThat(line).startsWith("후보 3: 역삼 원룸");
		assertThat(line).contains("약 17분").contains("보증금 1000만원, 월세 60만원");
		// 지표가 비었다는 사실을 밝혀야 모델이 없는 값을 지어내지 않는다.
		assertThat(line).contains("수집된 지표: 없음");
	}

	@Test
	void aLongDescriptionIsTruncatedSoOneListingCannotFloodThePrompt() {
		String line = RecommendationPrompt.candidate(1, property("가".repeat(500)), 10, List.of(), MAX_DESCRIPTION);

		assertThat(line).contains("가".repeat(MAX_DESCRIPTION) + "…");
		assertThat(line).doesNotContain("가".repeat(MAX_DESCRIPTION + 1));
	}

	@Test
	void missingOptionalFieldsAreSpelledOutRatherThanLeftBlank() {
		Property bare = Property.builder()
			.name("이름").address("주소").roadAddress("도로명").sggCode("11680").umdName("역삼동")
			.lat(37.5).lng(127.0).propertyType("원룸").leaseType(LeaseType.JEONSE).deposit(5000).monthlyRent(0)
			.build();

		assertThat(RecommendationPrompt.candidate(1, bare, 5, List.of(), MAX_DESCRIPTION)).contains("정보 없음");
	}
}
