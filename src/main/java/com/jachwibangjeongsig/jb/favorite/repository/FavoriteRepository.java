package com.jachwibangjeongsig.jb.favorite.repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.jachwibangjeongsig.jb.favorite.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    @EntityGraph(attributePaths = "property")
    Page<Favorite> findAllByUserId(UUID userId, Pageable pageable);
    @EntityGraph(attributePaths = "property")
    Optional<Favorite> findByUserIdAndPropertyId(UUID userId, UUID propertyId);
    boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);
    @Query("select f.property.id from Favorite f where f.userId = :userId and f.property.id in :propertyIds")
    List<UUID> findFavoritedPropertyIds(@Param("userId") UUID userId, @Param("propertyIds") List<UUID> propertyIds);
    @Modifying
    @Query("delete from Favorite f where f.userId = :userId and f.property.id = :propertyId")
    int deleteByUserIdAndPropertyId(
        @Param("userId") UUID userId,
        @Param("propertyId") UUID propertyId);
}
