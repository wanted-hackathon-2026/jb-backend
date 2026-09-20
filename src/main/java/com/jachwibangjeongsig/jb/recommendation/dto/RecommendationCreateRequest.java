package com.jachwibangjeongsig.jb.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jachwibangjeongsig.jb.recommendation.entity.TransportType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RecommendationCreateRequest(
	@NotNull UUID workplaceId,
	@NotNull TransportType transportType,
	@NotNull @Min(5) @Max(180) Integer maxCommuteMinutes,
	@NotNull @Min(1) @Max(5) Integer sunlightImportance,
	@NotNull @Min(1) @Max(5) Integer quietnessImportance,
	@NotNull @Min(1) @Max(5) Integer safetyImportance,
	@NotNull @Min(1) @Max(5) Integer infrastructureImportance,
	@NotNull @PositiveOrZero Integer depositMin,
	@NotNull @PositiveOrZero Integer depositMax,
	@NotNull @PositiveOrZero Integer monthlyRentMin,
	@NotNull @PositiveOrZero Integer monthlyRentMax,
	@NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 20) String> roomTypes
) {

	@JsonIgnore
	@AssertTrue(message = "최솟값은 최댓값보다 클 수 없습니다.")
	public boolean isRangeValid() {
		// 개별 필드 누락은 각자의 제약이 따로 알리게 둔다.
		if (depositMin == null || depositMax == null || monthlyRentMin == null || monthlyRentMax == null) {
			return true;
		}
		return depositMin <= depositMax && monthlyRentMin <= monthlyRentMax;
	}

	@JsonIgnore
	@AssertTrue(message = "매물 유형을 모두 합친 길이가 너무 깁니다.")
	public boolean isRoomTypesWithinColumnLimit() {
		// 쉼표로 이어 한 컬럼(VARCHAR(255))에 담기 때문에 합산 길이를 미리 막는다.
		return roomTypes == null || String.join(",", roomTypes).length() <= 255;
	}
}
