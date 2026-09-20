package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.dto.PropertyImageResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface PropertyImageService {

	PropertyImageResponse upload(UUID propertyId, List<MultipartFile> files);

	PropertyImageResponse.Image replace(UUID propertyId, UUID imageId, MultipartFile file);

	void delete(UUID propertyId, UUID imageId);

	Resource read(UUID propertyId, String filename);
}
