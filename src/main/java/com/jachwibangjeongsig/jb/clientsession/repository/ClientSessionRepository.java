package com.jachwibangjeongsig.jb.clientsession.repository;

import com.jachwibangjeongsig.jb.clientsession.entity.ClientSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientSessionRepository extends JpaRepository<ClientSession, UUID> {

	Optional<ClientSession> findBySessionHashToken(String sessionHashToken);
}
