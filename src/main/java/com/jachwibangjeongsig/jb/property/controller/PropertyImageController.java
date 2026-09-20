package com.jachwibangjeongsig.jb.property.controller;

import com.jachwibangjeongsig.jb.property.service.PropertyImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/property-images")
public class PropertyImageController {

	private final PropertyImageService imageService;

	public PropertyImageController(PropertyImageService imageService) {
		this.imageService = imageService;
	}

	@GetMapping("/{propertyId}/{filename}")
	public ResponseEntity<Resource> read(@PathVariable UUID propertyId, @PathVariable String filename) {
		MediaType mediaType = filename.endsWith(".png") ? MediaType.IMAGE_PNG
			: filename.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
		return ResponseEntity.ok().contentType(mediaType)
			.cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
			.body(imageService.read(propertyId, filename));
	}
}
