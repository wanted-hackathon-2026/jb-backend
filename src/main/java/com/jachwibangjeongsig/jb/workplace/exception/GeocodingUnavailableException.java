package com.jachwibangjeongsig.jb.workplace.exception;

public class GeocodingUnavailableException extends RuntimeException {

	public GeocodingUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public GeocodingUnavailableException(String message) {
		super(message);
	}
}
