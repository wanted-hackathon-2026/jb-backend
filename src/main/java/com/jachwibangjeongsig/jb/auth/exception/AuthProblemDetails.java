package com.jachwibangjeongsig.jb.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.time.OffsetDateTime;

public final class AuthProblemDetails {

	private AuthProblemDetails() {
	}

	public static ProblemDetail create(
		HttpStatus status,
		String code,
		String detail,
		HttpServletRequest request
	) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setInstance(java.net.URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("timestamp", OffsetDateTime.now());
		return problem;
	}
}
