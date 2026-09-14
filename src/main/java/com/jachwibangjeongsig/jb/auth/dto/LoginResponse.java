package com.jachwibangjeongsig.jb.auth.dto;

import com.jachwibangjeongsig.jb.auth.service.AuthService;

public record LoginResponse(
	String accessToken,
	String tokenType,
	long expiresIn,
	boolean isNewUser,
	UserResponse user
) {

	public static LoginResponse from(AuthService.LoginResult result) {
		return new LoginResponse(
			result.tokens().accessToken(),
			"Bearer",
			result.tokens().accessTokenExpiresIn(),
			result.newUser(),
			UserResponse.from(result.user())
		);
	}
}
