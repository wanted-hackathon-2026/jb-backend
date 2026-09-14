package com.jachwibangjeongsig.jb.auth.dto;

public record IssuedTokens(
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn
) {
}
