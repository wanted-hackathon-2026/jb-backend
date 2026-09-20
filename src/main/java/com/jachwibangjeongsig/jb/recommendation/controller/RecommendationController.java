package com.jachwibangjeongsig.jb.recommendation.controller;

import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationAcceptedResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationCreateRequest;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendationStatusResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyDetailResponse;
import com.jachwibangjeongsig.jb.recommendation.dto.RecommendedPropertyResponse;
import com.jachwibangjeongsig.jb.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@PostMapping
	public ResponseEntity<RecommendationAcceptedResponse> request(@Valid @RequestBody RecommendationCreateRequest body,
		@AuthenticationPrincipal Jwt jwt) {
		// 처리는 비동기라 202 로 접수만 알린다. 완료 여부는 상태 조회로 확인한다.
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(RecommendationAcceptedResponse.from(recommendationService.request(userId(jwt), body)));
	}

	@GetMapping("/{recommendationId}")
	public RecommendationStatusResponse status(@PathVariable UUID recommendationId, @AuthenticationPrincipal Jwt jwt) {
		return RecommendationStatusResponse.from(recommendationService.status(userId(jwt), recommendationId));
	}

	@GetMapping("/{recommendationId}/properties")
	public RecommendedPropertyResponse properties(@PathVariable UUID recommendationId,
		@AuthenticationPrincipal Jwt jwt) {
		return recommendationService.properties(userId(jwt), recommendationId);
	}

	@GetMapping("/{recommendationId}/properties/{propertyId}")
	public RecommendedPropertyDetailResponse property(@PathVariable UUID recommendationId,
		@PathVariable UUID propertyId, @AuthenticationPrincipal Jwt jwt) {
		return recommendationService.property(userId(jwt), recommendationId, propertyId);
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
