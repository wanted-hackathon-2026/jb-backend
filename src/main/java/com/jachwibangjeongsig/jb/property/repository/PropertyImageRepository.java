package com.jachwibangjeongsig.jb.property.repository;

import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, UUID> {

	long countByPropertyId(UUID propertyId);

	Optional<PropertyImage> findTopByPropertyIdOrderByDisplayOrderDesc(UUID propertyId);

	Optional<PropertyImage> findByPropertyIdAndStorageKey(UUID propertyId, String storageKey);
}
