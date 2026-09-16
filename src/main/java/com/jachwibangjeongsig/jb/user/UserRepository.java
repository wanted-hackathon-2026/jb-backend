package com.jachwibangjeongsig.jb.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByProviderAndProviderId(String provider, String providerId);

	boolean existsByNicknameAndIdNot(String nickname, UUID id);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query(value = """
		INSERT INTO users (id, provider, provider_id, email, nickname, role, created_at, updated_at)
		VALUES (:id, 'google', :providerId, :email, NULL, 'USER', :now, :now)
		ON DUPLICATE KEY UPDATE id = users.id
		""", nativeQuery = true)
	int insertGoogleUserIfAbsent(
		@org.springframework.data.repository.query.Param("id") UUID id,
		@org.springframework.data.repository.query.Param("providerId") String providerId,
		@org.springframework.data.repository.query.Param("email") String email,
		@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
