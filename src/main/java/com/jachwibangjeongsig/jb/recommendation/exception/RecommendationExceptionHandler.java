package com.jachwibangjeongsig.jb.recommendation.exception;

import com.jachwibangjeongsig.jb.auth.exception.AuthProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.jachwibangjeongsig.jb.recommendation.controller")
public class RecommendationExceptionHandler {

	@ExceptionHandler(RecommendationNotFoundException.class)
	ResponseEntity<Map<String, Object>> notFound(RecommendationNotFoundException exception,
		HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(RecommendationWorkplaceNotFoundException.class)
	ResponseEntity<Map<String, Object>> workplaceNotFound(RecommendationWorkplaceNotFoundException exception,
		HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "WORKPLACE_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler(RecommendationNotReadyException.class)
	ResponseEntity<Map<String, Object>> notReady(RecommendationNotReadyException exception,
		HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "RECOMMENDATION_NOT_READY", exception.getMessage(), request);
	}

	private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code,
		String detail, HttpServletRequest request) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(AuthProblemDetails.response(status, code, detail, request));
	}
}
