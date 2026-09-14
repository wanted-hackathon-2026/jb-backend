package com.jachwibangjeongsig.jb.auth.repository;

import com.jachwibangjeongsig.jb.auth.entity.RefreshTokenSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, UUID> {

	Optional<RefreshTokenSession> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshTokenSession> findLockedByTokenHash(String tokenHash);

	List<RefreshTokenSession> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);
}
