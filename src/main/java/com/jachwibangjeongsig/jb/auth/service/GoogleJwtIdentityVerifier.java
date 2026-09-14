package com.jachwibangjeongsig.jb.auth.service;

import com.jachwibangjeongsig.jb.auth.config.AuthProperties;
import com.jachwibangjeongsig.jb.auth.dto.GoogleIdentity;
import com.jachwibangjeongsig.jb.auth.exception.GoogleAuthenticationUnavailableException;
import com.jachwibangjeongsig.jb.auth.exception.InvalidGoogleIdentityTokenException;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.io.IOException;

@Component
public class GoogleJwtIdentityVerifier implements GoogleIdentityVerifier {

	private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
	private static final List<String> ALLOWED_ISSUERS = List.of(
		"https://accounts.google.com",
		"accounts.google.com"
	);

	private final NimbusJwtDecoder decoder;

	public GoogleJwtIdentityVerifier(AuthProperties properties) {
		this.decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
		OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefault();
		OAuth2TokenValidator<Jwt> issuer = jwt -> jwt.getIssuer() != null
			&& ALLOWED_ISSUERS.contains(jwt.getIssuer().toString())
			? OAuth2TokenValidatorResult.success()
			: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid issuer", null));
		OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.googleClientId())
			? OAuth2TokenValidatorResult.success()
			: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, issuer, audience));
	}

	@Override
	public GoogleIdentity verify(String idToken) {
		try {
			Jwt jwt = decoder.decode(idToken);
			String providerId = jwt.getSubject();
			String email = jwt.getClaimAsString("email");
			if (providerId == null || providerId.isBlank() || email == null || email.isBlank()) {
				throw new InvalidGoogleIdentityTokenException();
			}
			return new GoogleIdentity(providerId, email);
		} catch (InvalidGoogleIdentityTokenException exception) {
			throw exception;
		} catch (JwtException exception) {
			if (hasCause(exception, IOException.class)) {
				throw new GoogleAuthenticationUnavailableException(exception);
			}
			throw new InvalidGoogleIdentityTokenException();
		} catch (RuntimeException exception) {
			throw new GoogleAuthenticationUnavailableException(exception);
		}
	}

	private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
		Throwable current = throwable;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
