package com.jachwibangjeongsig.jb.workplace.controller;

import com.jachwibangjeongsig.jb.workplace.dto.WorkplaceCreateRequest;
import com.jachwibangjeongsig.jb.workplace.dto.WorkplaceResponse;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.service.WorkplaceService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceController {

	private final WorkplaceService workplaceService;

	public WorkplaceController(WorkplaceService workplaceService) {
		this.workplaceService = workplaceService;
	}

	@PostMapping
	public ResponseEntity<WorkplaceResponse> create(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody WorkplaceCreateRequest request
	) {
		Workplace workplace = workplaceService.create(userId(jwt), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(WorkplaceResponse.from(workplace));
	}

	@GetMapping
	public List<WorkplaceResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
		return workplaceService.findAll(userId(jwt)).stream()
			.map(WorkplaceResponse::from)
			.toList();
	}

	private UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
