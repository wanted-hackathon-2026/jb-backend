package com.jachwibangjeongsig.jb.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
	String googleClientId,
	String accessTokenSecret,
	String refreshTokenSecret,
	boolean cookieSecure,
	List<String> allowedOrigins
) {
}
