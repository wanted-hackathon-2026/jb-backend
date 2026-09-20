package com.jachwibangjeongsig.jb.property.service;

import com.jachwibangjeongsig.jb.property.exception.PropertyImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalPropertyImageStorage {

	private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
	private final Path root;

	public LocalPropertyImageStorage(@Value("${property.image-directory:data/property-images}") String directory) {
		this.root = Path.of(directory).toAbsolutePath().normalize();
	}

	public String validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw error(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY_IMAGE", "빈 사진은 등록할 수 없습니다.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw error(HttpStatus.PAYLOAD_TOO_LARGE, "PROPERTY_IMAGE_TOO_LARGE", "사진은 장당 10MB 이하여야 합니다.");
		}
		try (InputStream input = file.getInputStream()) {
			byte[] header = input.readNBytes(12);
			if (isJpeg(header)) return "jpg";
			if (isPng(header)) return "png";
			if (isWebp(header)) return "webp";
		} catch (IOException exception) {
			throw storageFailure(exception);
		}
		throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_PROPERTY_IMAGE_TYPE",
			"JPEG, PNG, WEBP 사진만 등록할 수 있습니다.");
	}

	public String store(UUID propertyId, MultipartFile file, String extension) {
		String filename = UUID.randomUUID() + "." + extension;
		String storageKey = propertyId + "/" + filename;
		Path target = resolve(storageKey);
		try {
			Files.createDirectories(target.getParent());
			try (InputStream input = file.getInputStream()) {
				Files.copy(input, target);
			}
			return storageKey;
		} catch (IOException exception) {
			throw storageFailure(exception);
		}
	}

	public Resource load(String storageKey) {
		try {
			Resource resource = new UrlResource(resolve(storageKey).toUri());
			if (!resource.isReadable()) {
				throw error(HttpStatus.NOT_FOUND, "PROPERTY_IMAGE_NOT_FOUND", "사진을 찾을 수 없습니다.");
			}
			return resource;
		} catch (IOException exception) {
			throw storageFailure(exception);
		}
	}

	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		} catch (IOException ignored) {
			// Best-effort cleanup after a failed request.
		}
	}

	private Path resolve(String storageKey) {
		Path path = root.resolve(storageKey).normalize();
		if (!path.startsWith(root)) {
			throw error(HttpStatus.BAD_REQUEST, "INVALID_PROPERTY_IMAGE", "잘못된 사진 경로입니다.");
		}
		return path;
	}

	private boolean isJpeg(byte[] bytes) {
		return bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
			&& unsigned(bytes[2]) == 0xff;
	}

	private boolean isPng(byte[] bytes) {
		byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		if (bytes.length < signature.length) return false;
		for (int index = 0; index < signature.length; index++) {
			if (bytes[index] != signature[index]) return false;
		}
		return true;
	}

	private boolean isWebp(byte[] bytes) {
		return bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
			&& new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
	}

	private int unsigned(byte value) {
		return value & 0xff;
	}

	private PropertyImageException storageFailure(Exception exception) {
		return new PropertyImageException(HttpStatus.INTERNAL_SERVER_ERROR, "PROPERTY_IMAGE_STORAGE_FAILED",
			"사진 저장에 실패했습니다.", exception);
	}

	private PropertyImageException error(HttpStatus status, String code, String message) {
		return new PropertyImageException(status, code, message);
	}
}
