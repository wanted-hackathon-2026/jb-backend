package com.jachwibangjeongsig.jb.workplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkplaceCreateRequest(
	@NotBlank @Size(max = 50) String name,
	@NotBlank @Size(max = 255) String roadAddress
) {
}
