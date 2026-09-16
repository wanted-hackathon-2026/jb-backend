package com.jachwibangjeongsig.jb.auth.service;

import com.jachwibangjeongsig.jb.auth.dto.GoogleIdentity;
import com.jachwibangjeongsig.jb.auth.dto.IssuedTokens;
import com.jachwibangjeongsig.jb.auth.entity.RefreshTokenSession;
import com.jachwibangjeongsig.jb.auth.exception.InvalidRefreshTokenException;
import com.jachwibangjeongsig.jb.auth.repository.RefreshTokenSessionRepository;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

	private static final String GOOGLE = "google";

	private final GoogleIdentityVerifier googleIdentityVerifier;
	private final UserRepository userRepository;
	private final RefreshTokenSessionRepository refreshTokenSessionRepository;
	private final JwtTokenService jwtTokenService;

	public AuthServiceImpl(
		GoogleIdentityVerifier googleIdentityVerifier,
		UserRepository userRepository,
		RefreshTokenSessionRepository refreshTokenSessionRepository,
		JwtTokenService jwtTokenService
	) {
		this.googleIdentityVerifier = googleIdentityVerifier;
		this.userRepository = userRepository;
		this.refreshTokenSessionRepository = refreshTokenSessionRepository;
		this.jwtTokenService = jwtTokenService;
	}

	@Override
	@Transactional
	public LoginResult loginWithGoogle(String idToken) {
		GoogleIdentity identity = googleIdentityVerifier.verify(idToken);
		UserLookup lookup = findOrCreateGoogleUser(identity);
		User user = lookup.user();
		if (!user.getEmail().equals(identity.email())) {
			user.updateEmail(identity.email());
		}

		IssuedTokens tokens = issueTokens(user, UUID.randomUUID());
		return new LoginResult(user, lookup.created(), tokens);
	}

	@Override
	@Transactional
	public ReissueResult reissue(String refreshToken) {
		JwtTokenService.RefreshTokenClaims claims = jwtTokenService.verifyRefreshToken(refreshToken);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		RefreshTokenSession current = refreshTokenSessionRepository
			.findLockedByTokenHash(TokenHash.sha256(refreshToken))
			.orElseThrow(InvalidRefreshTokenException::new);

		if (!current.getUser().getId().equals(claims.userId()) || !current.isUsableAt(now)) {
			revokeFamily(current.getFamilyId(), now);
			throw new InvalidRefreshTokenException();
		}

		current.revoke(now);
		IssuedTokens tokens = issueTokens(current.getUser(), current.getFamilyId());
		return new ReissueResult(tokens);
	}

	@Override
	@Transactional
	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			return;
		}
		refreshTokenSessionRepository.findByTokenHash(TokenHash.sha256(refreshToken))
			.ifPresent(session -> session.revoke(LocalDateTime.now(ZoneOffset.UTC)));
	}

	private UserLookup findOrCreateGoogleUser(GoogleIdentity identity) {
		// The unique provider identity serializes only logins for the same account,
		// including requests handled by different application instances.
		UUID candidateId = UUID.randomUUID();
		userRepository.insertGoogleUserIfAbsent(candidateId, identity.providerId(), identity.email(),
			LocalDateTime.now(ZoneOffset.UTC));
		User user = userRepository.findByProviderAndProviderId(GOOGLE, identity.providerId()).orElseThrow();
		return new UserLookup(user, user.getId().equals(candidateId));
	}

	private IssuedTokens issueTokens(User user, UUID familyId) {
		Instant now = Instant.now();
		UUID tokenId = UUID.randomUUID();
		IssuedTokens tokens = jwtTokenService.issue(user, tokenId, now);
		refreshTokenSessionRepository.save(RefreshTokenSession.create(
			user,
			familyId,
			TokenHash.sha256(tokens.refreshToken()),
			LocalDateTime.ofInstant(now.plus(JwtTokenService.REFRESH_TOKEN_TTL), ZoneOffset.UTC)
		));
		return tokens;
	}

	private void revokeFamily(UUID familyId, LocalDateTime now) {
		refreshTokenSessionRepository.findAllByFamilyIdAndRevokedAtIsNull(familyId)
			.forEach(session -> session.revoke(now));
	}

	private record UserLookup(User user, boolean created) {
	}
}
