package com.jachwibangjeongsig.jb.property.repository;
import com.jachwibangjeongsig.jb.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {}
