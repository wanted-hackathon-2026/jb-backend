package com.jachwibangjeongsig.jb.recommendation.controller;

import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationAcceptedResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationCreateRequest;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationHistoryResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationStatusResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyDetailResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyResponse;
import com.jachwibangjeongsig.jb.clientsession.service.ClientSessionService;
import com.jachwibangjeongsig.jb.recommendation.exception.ClientSessionRequiredException;
import com.jachwibangjeongsig.jb.recommendation.service.RecommendationOwner;
import com.jachwibangjeongsig.jb.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

	private static final String CLIENT_SESSION_HEADER = "X-Client-Session";

	/** 어떤 행과도 맞지 않는 id. 모르는 토큰의 조회를 404 로 흘려보내는 용도다. */
	private static final UUID UNKNOWN_SESSION = new UUID(0, 0);

	private final RecommendationService recommendationService;
	private final ClientSessionService clientSessions;

	public RecommendationController(RecommendationService recommendationService,
		ClientSessionService clientSessions) {
		this.recommendationService = recommendationService;
		this.clientSessions = clientSessions;
	}

	@PostMapping
	public ResponseEntity<RecommendationAcceptedResponse> request(@Valid @RequestBody RecommendationCreateRequest body,
		@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = CLIENT_SESSION_HEADER, required = false) String clientSession) {
		// 처리는 비동기라 202 로 접수만 알린다. 완료 여부는 상태 조회로 확인한다.
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(RecommendationAcceptedResponse.from(
				recommendationService.request(ownerForRequest(jwt, clientSession), body)));
	}

	@GetMapping
	public RecommendationHistoryResponse history(
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = CLIENT_SESSION_HEADER, required = false) String clientSession) {
		return recommendationService.history(owner(jwt, clientSession), page, size);
	}

	@GetMapping("/{recommendationId}")
	public RecommendationStatusResponse status(@PathVariable UUID recommendationId,
		@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = CLIENT_SESSION_HEADER, required = false) String clientSession) {
		return RecommendationStatusResponse.from(
			recommendationService.status(owner(jwt, clientSession), recommendationId));
	}

	@GetMapping("/{recommendationId}/properties")
	public RecommendedPropertyResponse properties(@PathVariable UUID recommendationId,
		@AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = CLIENT_SESSION_HEADER, required = false) String clientSession) {
		return recommendationService.properties(owner(jwt, clientSession), recommendationId);
	}

	@GetMapping("/{recommendationId}/properties/{propertyId}")
	public RecommendedPropertyDetailResponse property(@PathVariable UUID recommendationId,
		@PathVariable UUID propertyId, @AuthenticationPrincipal Jwt jwt,
		@RequestHeader(value = CLIENT_SESSION_HEADER, required = false) String clientSession) {
		return recommendationService.property(owner(jwt, clientSession), recommendationId, propertyId);
	}

	/** 접수 때만 세션을 만든다. 처음 보는 토큰으로 남의 추천을 조회할 이유는 없다. */
	private RecommendationOwner ownerForRequest(Jwt jwt, String clientSession) {
		if (jwt != null) {
			return RecommendationOwner.ofUser(UUID.fromString(jwt.getSubject()));
		}
		requirePresent(clientSession);
		return RecommendationOwner.ofClientSession(clientSessions.resolveOrCreate(clientSession).getId());
	}

	/**
	 * 조회는 이미 있는 세션만 인정한다. 없는 토큰이면 어차피 소유자가 아니므로
	 * 존재하지 않는 세션 id 로 조회하게 두어 404 로 떨어뜨린다.
	 */
	private RecommendationOwner owner(Jwt jwt, String clientSession) {
		if (jwt != null) {
			return RecommendationOwner.ofUser(UUID.fromString(jwt.getSubject()));
		}
		requirePresent(clientSession);
		return RecommendationOwner.ofClientSession(
			clientSessions.find(clientSession).map(session -> session.getId()).orElse(UNKNOWN_SESSION));
	}

	private static void requirePresent(String clientSession) {
		if (clientSession == null || clientSession.isBlank()) {
			throw new ClientSessionRequiredException();
		}
	}

}
