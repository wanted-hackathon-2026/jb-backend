package com.jachwibangjeongsig.jb.auth.exception;

import java.util.Map;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
		HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
		MissingServletRequestParameterException.class})
	ResponseEntity<Map<String, Object>> invalidRequest(Exception exception, HttpServletRequest request) {
		return response(
			HttpStatus.BAD_REQUEST,
			"INVALID_REQUEST",
			"요청 형식이 올바르지 않습니다.",
			request
		);
	}

	@ExceptionHandler(InvalidGoogleIdentityTokenException.class)
	ResponseEntity<Map<String, Object>> invalidGoogleToken(
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
	ResponseEntity<Map<String, Object>> invalidRefreshToken(
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
	ResponseEntity<Map<String, Object>> googleUnavailable(
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

	private ResponseEntity<Map<String, Object>> response(
		HttpStatus status,
		String code,
		String detail,
		HttpServletRequest request
	) {
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(AuthProblemDetails.response(status, code, detail, request));
	}
}
