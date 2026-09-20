package com.jachwibangjeongsig.jb.property.repository;

import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, UUID> {

	long countByPropertyId(UUID propertyId);

	Optional<PropertyImage> findTopByPropertyIdOrderByDisplayOrderDesc(UUID propertyId);

	Optional<PropertyImage> findByPropertyIdAndStorageKey(UUID propertyId, String storageKey);

	List<PropertyImage> findByPropertyIdOrderByDisplayOrderAsc(UUID propertyId);

	@Query("select i.property.id as propertyId, i.storageKey as storageKey from PropertyImage i "
		+ "where i.property.id in :propertyIds and i.displayOrder = 0")
	List<Thumbnail> findThumbnails(@Param("propertyIds") List<UUID> propertyIds);

	interface Thumbnail {
		UUID getPropertyId();
		String getStorageKey();
	}
}
