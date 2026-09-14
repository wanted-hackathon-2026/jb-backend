package com.jachwibangjeongsig.jb.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Refresh token is invalid");
	}
}
