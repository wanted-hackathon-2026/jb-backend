package com.jachwibangjeongsig.jb.user;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByProviderAndProviderId(String provider, String providerId);

	boolean existsByNicknameAndIdNot(String nickname, UUID id);

	@Modifying
	@Query(value = """
		INSERT INTO users (id, provider, provider_id, email, nickname, role, created_at, updated_at)
		VALUES (:id, 'google', :providerId, :email, NULL, 'USER', :now, :now)
		ON DUPLICATE KEY UPDATE id = users.id
		""", nativeQuery = true)
	int insertGoogleUserIfAbsent(
		@Param("id") UUID id,
		@Param("providerId") String providerId,
		@Param("email") String email,
		@Param("now") LocalDateTime now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
