package com.jachwibangjeongsig.jb.workplace.exception;

import com.jachwibangjeongsig.jb.auth.exception.AuthProblemDetails;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingUnavailableException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class WorkplaceExceptionHandler {

	@ExceptionHandler(AddressNotGeocodableException.class)
	ResponseEntity<Map<String, Object>> notGeocodable(
		AddressNotGeocodableException exception,
		HttpServletRequest request
	) {
		return response(
			HttpStatus.BAD_REQUEST,
			"ADDRESS_NOT_GEOCODABLE",
			"주소의 좌표를 찾을 수 없습니다. 주소를 다시 선택해 주세요.",
			request
		);
	}

	@ExceptionHandler(GeocodingUnavailableException.class)
	ResponseEntity<Map<String, Object>> unavailable(
		GeocodingUnavailableException exception,
		HttpServletRequest request
	) {
		return response(
			HttpStatus.BAD_GATEWAY,
			"GEOCODING_UNAVAILABLE",
			"주소 좌표 변환 서비스를 이용할 수 없습니다.",
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
