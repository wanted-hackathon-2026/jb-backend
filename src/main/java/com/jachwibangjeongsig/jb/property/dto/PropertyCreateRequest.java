package com.jachwibangjeongsig.jb.property.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

public record PropertyCreateRequest(
	@NotBlank @Size(max = 100) String name,
	@NotBlank @Size(max = 255) String address,
	@NotBlank @Size(max = 255) String roadAddress,
	@NotBlank @Pattern(regexp = "[0-9]{5}") String sggCode,
	@NotBlank @Size(max = 50) String umdName,
	@NotBlank @Size(max = 20) String propertyType,
	@NotNull LeaseType leaseType,
	@NotNull @PositiveOrZero @JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer deposit,
	@NotNull @PositiveOrZero @JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer monthlyRent,
	@Positive @Digits(integer = 6, fraction = 2) BigDecimal exclusiveArea,
	@Positive @Digits(integer = 6, fraction = 2) BigDecimal supplyArea,
	@JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer floor,
	@Positive @JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer bathroomCount,
	@Positive @JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer totalFloors,
	@Positive @JsonDeserialize(using = PropertyIntegerDeserializer.class) Integer buildYear,
	@Pattern(regexp = "(?s).*\\S.*") @Size(max = 10) String direction,
	// Keep even a UTF-8 description safely within MySQL TEXT's byte limit.
	@Size(max = 16383) String description
) {
	@JsonIgnore
	@AssertTrue(message = "전세는 월세가 0이어야 하고, 월세 매물은 월세가 0보다 커야 합니다.")
	public boolean isLeasePriceValid() {
		// Let the field constraints report missing values independently.
		if (leaseType == null || monthlyRent == null) {
			return true;
		}
		return switch (leaseType) {
			case JEONSE -> monthlyRent == 0;
			case MONTHLY -> monthlyRent > 0;
		};
	}
}
