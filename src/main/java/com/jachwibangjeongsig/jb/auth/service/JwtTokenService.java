package com.jachwibangjeongsig.jb.auth.service;

import com.jachwibangjeongsig.jb.auth.config.AuthProperties;
import com.jachwibangjeongsig.jb.auth.dto.IssuedTokens;
import com.jachwibangjeongsig.jb.auth.exception.InvalidRefreshTokenException;

import com.jachwibangjeongsig.jb.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenService {

	public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

	private final JwtEncoder accessTokenEncoder;
	private final JwtEncoder refreshTokenEncoder;
	private final JwtDecoder refreshTokenDecoder;

	public JwtTokenService(AuthProperties properties) {
		SecretKey accessKey = secretKey(properties.accessTokenSecret());
		SecretKey refreshKey = secretKey(properties.refreshTokenSecret());
		this.accessTokenEncoder = NimbusJwtEncoder.withSecretKey(accessKey).build();
		this.refreshTokenEncoder = NimbusJwtEncoder.withSecretKey(refreshKey).build();
		NimbusJwtDecoder refreshDecoder = NimbusJwtDecoder.withSecretKey(refreshKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		refreshDecoder.setJwtValidator(JwtValidators.createDefault());
		this.refreshTokenDecoder = refreshDecoder;
	}

	public IssuedTokens issue(User user, UUID refreshTokenId, Instant now) {
		String accessToken = encodeAccessToken(user, now);
		String refreshToken = encodeRefreshToken(user, refreshTokenId, now);
		return new IssuedTokens(accessToken, refreshToken, ACCESS_TOKEN_TTL.toSeconds());
	}

	public RefreshTokenClaims verifyRefreshToken(String token) {
		try {
			var jwt = refreshTokenDecoder.decode(token);
			if (!"refresh".equals(jwt.getClaimAsString("token_type")) || jwt.getSubject() == null) {
				throw new InvalidRefreshTokenException();
			}
			return new RefreshTokenClaims(
				UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getId()),
				jwt.getExpiresAt()
			);
		} catch (InvalidRefreshTokenException exception) {
			throw exception;
		} catch (JwtException | IllegalArgumentException exception) {
			throw new InvalidRefreshTokenException();
		}
	}

	private String encodeAccessToken(User user, Instant now) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(user.getId().toString())
			.issuedAt(now)
			.expiresAt(now.plus(ACCESS_TOKEN_TTL))
			.claim("role", user.getRole().name())
			.claim("token_type", "access")
			.build();
		return encode(accessTokenEncoder, claims);
	}

	private String encodeRefreshToken(User user, UUID refreshTokenId, Instant now) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(user.getId().toString())
			.id(refreshTokenId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(REFRESH_TOKEN_TTL))
			.claim("token_type", "refresh")
			.build();
		return encode(refreshTokenEncoder, claims);
	}

	private String encode(JwtEncoder encoder, JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private SecretKey secretKey(String value) {
		if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT secret must contain at least 32 bytes");
		}
		return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	public record RefreshTokenClaims(UUID userId, UUID tokenId, Instant expiresAt) {
	}
}
