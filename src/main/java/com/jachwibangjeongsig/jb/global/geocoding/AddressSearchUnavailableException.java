package com.jachwibangjeongsig.jb.global.geocoding;

public class AddressSearchUnavailableException extends RuntimeException {

	public AddressSearchUnavailableException(String message) {
		super(message);
	}

	public AddressSearchUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
