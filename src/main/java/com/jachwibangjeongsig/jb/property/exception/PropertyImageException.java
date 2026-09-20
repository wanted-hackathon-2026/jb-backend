package com.jachwibangjeongsig.jb.property.exception;

import org.springframework.http.HttpStatus;

public class PropertyImageException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public PropertyImageException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public PropertyImageException(HttpStatus status, String code, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}
}
