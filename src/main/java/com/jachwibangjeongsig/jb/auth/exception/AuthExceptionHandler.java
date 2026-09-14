package com.jachwibangjeongsig.jb.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
	ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
		return response(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"요청 형식이 올바르지 않습니다.",
			request
		);
	}

	@ExceptionHandler(InvalidGoogleIdentityTokenException.class)
	ResponseEntity<ProblemDetail> invalidGoogleToken(
		InvalidGoogleIdentityTokenException exception,
		HttpServletRequest request
	) {
		return response(
			HttpStatus.UNAUTHORIZED,
			"INVALID_GOOGLE_TOKEN",
			"Google 인증에 실패했습니다.",
			request
		);
	}

	@ExceptionHandler(InvalidRefreshTokenException.class)
	ResponseEntity<ProblemDetail> invalidRefreshToken(
		InvalidRefreshTokenException exception,
		HttpServletRequest request
	) {
		return response(
			HttpStatus.UNAUTHORIZED,
			"INVALID_REFRESH_TOKEN",
			"인증에 실패했습니다.",
			request
		);
	}

	@ExceptionHandler(GoogleAuthenticationUnavailableException.class)
	ResponseEntity<ProblemDetail> googleUnavailable(
		GoogleAuthenticationUnavailableException exception,
		HttpServletRequest request
	) {
		return response(
			HttpStatus.BAD_GATEWAY,
			"GOOGLE_AUTH_UNAVAILABLE",
			"Google 인증 서비스를 이용할 수 없습니다.",
			request
		);
	}

	private ResponseEntity<ProblemDetail> response(
		HttpStatus status,
		String code,
		String detail,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status)
			.contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
			.body(AuthProblemDetails.create(status, code, detail, request));
	}
}
