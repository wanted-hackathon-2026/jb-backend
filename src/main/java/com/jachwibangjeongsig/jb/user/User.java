package com.jachwibangjeongsig.jb.user;

import com.jachwibangjeongsig.jb.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
	name = "users",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_users_provider_provider_id",
		columnNames = {"provider", "provider_id"}
	)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(nullable = false, length = 20)
	private String provider;

	@Column(name = "provider_id", nullable = false, length = 255)
	private String providerId;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(length = 50)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Builder
	private User(String provider, String providerId, String email, String nickname, UserRole role) {
		this.provider = provider;
		this.providerId = providerId;
		this.email = email;
		this.nickname = nickname;
		this.role = role == null ? UserRole.USER : role;
	}

	public void updateEmail(String email) {
		this.email = email;
	}

	public void updateNickname(String nickname) {
		this.nickname = nickname;
	}
}
