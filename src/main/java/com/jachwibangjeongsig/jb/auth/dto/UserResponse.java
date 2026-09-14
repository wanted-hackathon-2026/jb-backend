package com.jachwibangjeongsig.jb.auth.dto;

import com.jachwibangjeongsig.jb.user.User;

import java.util.UUID;

public record UserResponse(
	UUID id,
	String email,
	String nickname,
	boolean profileCompleted
) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getNickname(),
			user.getNickname() != null
		);
	}
}
