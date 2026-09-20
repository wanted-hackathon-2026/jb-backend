package com.jachwibangjeongsig.jb.property.repository;
import com.jachwibangjeongsig.jb.property.entity.Property;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findByLatBetweenAndLngBetweenOrderByCreatedAtDesc(
        double minLat, double maxLat, double minLng, double maxLng, Pageable pageable);
}
