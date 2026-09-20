package com.jachwibangjeongsig.jb.workplace.dto;

import jakarta.validation.constraints.Size;

/**
 * 부분 수정 요청. 두 필드 모두 선택이며, 생략하면 해당 값을 바꾸지 않는다.
 * 앞뒤 공백을 제거한 뒤 검증하므로 공백만 담긴 값은 400으로 거절된다.
 */
public record WorkplaceUpdateRequest(
	@Size(min = 1, max = 50) String name,
	@Size(min = 1, max = 255) String roadAddress
) {

	public WorkplaceUpdateRequest {
		name = name == null ? null : name.trim();
		roadAddress = roadAddress == null ? null : roadAddress.trim();
	}
}
