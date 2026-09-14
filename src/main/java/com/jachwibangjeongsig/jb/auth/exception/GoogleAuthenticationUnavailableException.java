package com.jachwibangjeongsig.jb.auth.exception;

public class GoogleAuthenticationUnavailableException extends RuntimeException {

	public GoogleAuthenticationUnavailableException(Throwable cause) {
		super("Google authentication is unavailable", cause);
	}
}
