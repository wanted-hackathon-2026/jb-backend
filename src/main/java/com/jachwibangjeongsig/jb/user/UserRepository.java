package com.jachwibangjeongsig.jb.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByProviderAndProviderId(String provider, String providerId);

	boolean existsByNicknameAndIdNot(String nickname, UUID id);


	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
