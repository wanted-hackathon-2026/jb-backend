package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.dto.PropertyImageResponse;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import com.jachwibangjeongsig.jb.property.exception.PropertyImageException;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PropertyImageServiceImpl implements PropertyImageService {

	private static final int MAX_IMAGE_COUNT = 10;
	private final PropertyRepository propertyRepository;
	private final PropertyImageRepository imageRepository;
	private final LocalPropertyImageStorage storage;
	private final TransactionTemplate transactionTemplate;

	public PropertyImageServiceImpl(PropertyRepository propertyRepository, PropertyImageRepository imageRepository,
		LocalPropertyImageStorage storage, PlatformTransactionManager transactionManager) {
		this.propertyRepository = propertyRepository;
		this.imageRepository = imageRepository;
		this.storage = storage;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public PropertyImageResponse upload(UUID propertyId, List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw error(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY_IMAGE", "사진을 한 장 이상 선택해 주세요.");
		}
		Property property = propertyRepository.findById(propertyId)
			.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "매물을 찾을 수 없습니다."));
		long existingCount = imageRepository.countByPropertyId(propertyId);
		if (existingCount + files.size() > MAX_IMAGE_COUNT) {
			throw error(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY_IMAGE", "매물 사진은 최대 10장입니다.");
		}

		List<String> extensions = files.stream().map(storage::validate).toList();
		int firstOrder = imageRepository.findTopByPropertyIdOrderByDisplayOrderDesc(propertyId)
			.map(image -> image.getDisplayOrder() + 1).orElse(0);
		List<String> storedKeys = new ArrayList<>();
		try {
			for (int index = 0; index < files.size(); index++) {
				storedKeys.add(storage.store(propertyId, files.get(index), extensions.get(index)));
			}
			List<PropertyImage> saved = transactionTemplate.execute(status -> {
				List<PropertyImage> images = new ArrayList<>();
				for (int index = 0; index < storedKeys.size(); index++) {
					images.add(new PropertyImage(property, storedKeys.get(index), firstOrder + index));
				}
				return imageRepository.saveAllAndFlush(images);
			});
			return new PropertyImageResponse(propertyId, saved.stream().map(PropertyImageResponse.Image::from).toList());
		} catch (RuntimeException exception) {
			storedKeys.forEach(storage::delete);
			throw exception;
		}
	}

	@Override
	public Resource read(UUID propertyId, String filename) {
		String storageKey = propertyId + "/" + filename;
		imageRepository.findByPropertyIdAndStorageKey(propertyId, storageKey)
			.orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PROPERTY_IMAGE_NOT_FOUND", "사진을 찾을 수 없습니다."));
		return storage.load(storageKey);
	}

	private PropertyImageException error(HttpStatus status, String code, String message) {
		return new PropertyImageException(status, code, message);
	}
}
