package com.jachwibangjeongsig.jb.clientsession.service;

import com.jachwibangjeongsig.jb.clientsession.entity.ClientSession;
import com.jachwibangjeongsig.jb.clientsession.repository.ClientSessionRepository;
import com.jachwibangjeongsig.jb.global.TokenHash;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class ClientSessionService {

	private static final Duration LIFETIME = Duration.ofDays(30);

	private final ClientSessionRepository clientSessionRepository;

	public ClientSessionService(ClientSessionRepository clientSessionRepository) {
		this.clientSessionRepository = clientSessionRepository;
	}

	/** 처음 보는 토큰이면 세션을 만들고, 이미 있으면 마지막 접근 시각만 갱신한다. */
	@Transactional
	public ClientSession resolveOrCreate(String rawToken) {
		String hash = TokenHash.sha256(rawToken);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		return clientSessionRepository.findBySessionHashToken(hash)
			.map(session -> {
				session.touch(now);
				return session;
			})
			.orElseGet(() -> clientSessionRepository.save(
				ClientSession.issue(hash, now, now.plus(LIFETIME))));
	}

	@Transactional(readOnly = true)
	public Optional<ClientSession> find(String rawToken) {
		return clientSessionRepository.findBySessionHashToken(TokenHash.sha256(rawToken));
	}
}
