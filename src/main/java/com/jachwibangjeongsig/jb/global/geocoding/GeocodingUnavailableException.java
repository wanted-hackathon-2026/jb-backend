package com.jachwibangjeongsig.jb.global.geocoding;

public class GeocodingUnavailableException extends RuntimeException {

	public GeocodingUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public GeocodingUnavailableException(String message) {
		super(message);
	}
}
