package com.jachwibangjeongsig.jb.clientsession.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 비로그인 사용자를 식별하는 세션. 클라이언트가 만든 랜덤 UUID의 SHA-256 해시만 보관한다 —
 * DB가 유출돼도 남의 추천을 조회할 원본 토큰이 나오지 않는다.
 */
@Getter
@Entity
@Table(name = "client_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientSession {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.RANDOM)
	@Column(columnDefinition = "BINARY(16)")
	private UUID id;

	@Column(name = "session_hash_token", nullable = false, length = 255)
	private String sessionHashToken;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "last_accessed_at", nullable = false)
	private LocalDateTime lastAccessedAt;

	public static ClientSession issue(String sessionHashToken, LocalDateTime now, LocalDateTime expiresAt) {
		ClientSession session = new ClientSession();
		session.sessionHashToken = sessionHashToken;
		session.expiresAt = expiresAt;
		session.createdAt = now;
		session.lastAccessedAt = now;
		return session;
	}

	public void touch(LocalDateTime now) {
		this.lastAccessedAt = now;
	}
}
