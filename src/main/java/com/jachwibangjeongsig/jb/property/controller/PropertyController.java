package com.jachwibangjeongsig.jb.property.controller;

import com.jachwibangjeongsig.jb.property.dto.PropertyCreateRequest;
import com.jachwibangjeongsig.jb.property.dto.PropertyResponse;
import com.jachwibangjeongsig.jb.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

	private final PropertyService propertyService;

	public PropertyController(PropertyService propertyService) {
		this.propertyService = propertyService;
	}

	@PostMapping
	public ResponseEntity<PropertyResponse> create(@Valid @RequestBody PropertyCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(PropertyResponse.from(propertyService.create(request)));
	}
}
