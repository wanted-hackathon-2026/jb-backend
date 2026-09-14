package com.jachwibangjeongsig.jb.auth.exception;

public class InvalidGoogleIdentityTokenException extends RuntimeException {

	public InvalidGoogleIdentityTokenException() {
		super("Google ID token is invalid");
	}
}
