package com.jachwibangjeongsig.jb.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

public final class AuthProblemDetails {

	private AuthProblemDetails() {
	}

	public static java.util.Map<String, Object> response(
		HttpStatus status, String code, String detail, HttpServletRequest request
	) {
		return java.util.Map.of(
			"type", "about:blank", "title", status.getReasonPhrase(),
			"status", status.value(), "detail", detail,
			"instance", request.getRequestURI(), "code", code,
			"timestamp", OffsetDateTime.now().toString()
		);
	}

}
