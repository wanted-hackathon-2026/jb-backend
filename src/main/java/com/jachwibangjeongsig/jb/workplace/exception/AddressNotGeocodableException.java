package com.jachwibangjeongsig.jb.workplace.exception;

public class AddressNotGeocodableException extends RuntimeException {

	public AddressNotGeocodableException() {
		super("Address could not be converted to coordinates");
	}
}
