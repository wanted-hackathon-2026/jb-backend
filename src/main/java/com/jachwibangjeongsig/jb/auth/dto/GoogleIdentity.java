package com.jachwibangjeongsig.jb.auth.dto;

public record GoogleIdentity(
	String providerId,
	String email
) {
}
