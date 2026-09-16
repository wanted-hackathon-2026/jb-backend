package com.jachwibangjeongsig.jb.favorite.repository;
import com.jachwibangjeongsig.jb.favorite.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    @EntityGraph(attributePaths = "property")
    Page<Favorite> findAllByUserId(UUID userId, Pageable pageable);
    @EntityGraph(attributePaths = "property")
    Optional<Favorite> findByUserIdAndPropertyId(UUID userId, UUID propertyId);
    boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Favorite f where f.userId = :userId and f.property.id = :propertyId")
    int deleteByUserIdAndPropertyId(
        @org.springframework.data.repository.query.Param("userId") UUID userId,
        @org.springframework.data.repository.query.Param("propertyId") UUID propertyId);
}
