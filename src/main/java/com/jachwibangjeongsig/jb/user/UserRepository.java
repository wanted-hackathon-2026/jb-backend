package com.jachwibangjeongsig.jb.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
