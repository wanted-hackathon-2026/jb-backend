package com.jachwibangjeongsig.jb.property.dto;

import com.jachwibangjeongsig.jb.property.entity.PropertyImage;

import java.util.List;
import java.util.UUID;

public record PropertyImageResponse(UUID propertyId, List<Image> images) {

	public record Image(UUID id, String url, int displayOrder) {
		public static Image from(PropertyImage image) {
			return new Image(image.getId(), "/api/property-images/" + image.getStorageKey(),
				image.getDisplayOrder());
		}
	}
}
