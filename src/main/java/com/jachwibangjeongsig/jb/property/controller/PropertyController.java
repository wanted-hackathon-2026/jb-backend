package com.jachwibangjeongsig.jb.property.controller;

import com.jachwibangjeongsig.jb.property.dto.PropertyCreateRequest;
import com.jachwibangjeongsig.jb.property.dto.PropertyDetailResponse;
import com.jachwibangjeongsig.jb.property.dto.PropertyImageResponse;
import com.jachwibangjeongsig.jb.property.dto.PropertyMapResponse;
import com.jachwibangjeongsig.jb.property.dto.PropertyResponse;
import com.jachwibangjeongsig.jb.property.service.PropertyImageService;
import com.jachwibangjeongsig.jb.property.service.PropertyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

	private final PropertyService propertyService;
	private final PropertyImageService propertyImageService;

	public PropertyController(PropertyService propertyService, PropertyImageService propertyImageService) {
		this.propertyService = propertyService;
		this.propertyImageService = propertyImageService;
	}

	@PostMapping
	public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(PropertyResponse.from(propertyService.create(request)));
	}

	@PostMapping(path = "/{propertyId}/images", consumes = "multipart/form-data")
	public ResponseEntity<PropertyImageResponse> uploadImages(@PathVariable UUID propertyId,
		@RequestPart("files") List<MultipartFile> files) {
		return ResponseEntity.status(HttpStatus.CREATED).body(propertyImageService.upload(propertyId, files));
	}

	@GetMapping("/map")
	public PropertyMapResponse map(@RequestParam double minLat, @RequestParam double maxLat,
		@RequestParam double minLng, @RequestParam double maxLng,
		@RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit, @AuthenticationPrincipal Jwt jwt) {
		return propertyService.findWithinMapBounds(minLat, maxLat, minLng, maxLng, limit, viewerUserId(jwt));
	}

	@GetMapping("/{propertyId}")
	public PropertyDetailResponse detail(@PathVariable UUID propertyId, @AuthenticationPrincipal Jwt jwt) {
		return propertyService.detail(propertyId, viewerUserId(jwt));
	}

	private static UUID viewerUserId(Jwt jwt) {
		return jwt == null ? null : UUID.fromString(jwt.getSubject());
	}
}
