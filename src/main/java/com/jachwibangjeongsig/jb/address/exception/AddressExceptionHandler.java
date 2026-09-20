package com.jachwibangjeongsig.jb.address.exception;

import com.jachwibangjeongsig.jb.auth.exception.AuthProblemDetails;
import com.jachwibangjeongsig.jb.global.geocoding.AddressSearchUnavailableException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.jachwibangjeongsig.jb.address.controller")
public class AddressExceptionHandler {

	/** 외부 키와 VWorld 원본 메시지는 응답에 넣지 않는다. */
	@ExceptionHandler(AddressSearchUnavailableException.class)
	ResponseEntity<Map<String, Object>> unavailable(
		AddressSearchUnavailableException exception,
		HttpServletRequest request
	) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(AuthProblemDetails.response(
				HttpStatus.BAD_GATEWAY,
				"ADDRESS_SEARCH_UNAVAILABLE",
				"주소 검색 서비스를 이용할 수 없습니다.",
				request
			));
	}
}
