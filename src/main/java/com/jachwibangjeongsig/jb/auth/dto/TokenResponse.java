package com.jachwibangjeongsig.jb.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse from(IssuedTokens tokens) {
		return new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessTokenExpiresIn());
	}
}
