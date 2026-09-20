package com.jachwibangjeongsig.jb.property.exception;

import com.jachwibangjeongsig.jb.auth.exception.AuthProblemDetails;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.jachwibangjeongsig.jb.property.controller")
public class PropertyExceptionHandler {

	@ExceptionHandler(PropertyImageException.class)
	ResponseEntity<Map<String, Object>> propertyImage(PropertyImageException exception,
		HttpServletRequest request) {
		return response(exception.status(), exception.code(), exception.getMessage(), request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<Map<String, Object>> imageTooLarge(HttpServletRequest request) {
		return response(HttpStatus.PAYLOAD_TOO_LARGE, "PROPERTY_IMAGE_TOO_LARGE",
			"사진은 장당 10MB 이하여야 합니다.", request);
	}

	@ExceptionHandler(PropertyAddressNotGeocodableException.class)
	ResponseEntity<Map<String, Object>> notGeocodable(HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "ADDRESS_NOT_GEOCODABLE",
			"주소의 좌표를 찾을 수 없습니다. 주소를 다시 선택해 주세요.", request);
	}

	@ExceptionHandler(GeocodingUnavailableException.class)
	ResponseEntity<Map<String, Object>> unavailable(HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, "GEOCODING_UNAVAILABLE",
			"주소 좌표 변환 서비스를 이용할 수 없습니다.", request);
	}

	private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code,
		String detail, HttpServletRequest request) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(AuthProblemDetails.response(status, code, detail, request));
	}
}
