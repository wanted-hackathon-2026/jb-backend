package com.jachwibangjeongsig.jb.auth.entity;

import com.jachwibangjeongsig.jb.global.entity.BaseTimeEntity;
import com.jachwibangjeongsig.jb.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_token_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenSession extends BaseTimeEntity {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "family_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	private RefreshTokenSession(User user, UUID familyId, String tokenHash, LocalDateTime expiresAt) {
		this.user = user;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public static RefreshTokenSession create(User user, UUID familyId, String tokenHash, LocalDateTime expiresAt) {
		return new RefreshTokenSession(user, familyId, tokenHash, expiresAt);
	}

	public boolean isUsableAt(LocalDateTime now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}
}
