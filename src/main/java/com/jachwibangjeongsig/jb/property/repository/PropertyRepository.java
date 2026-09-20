package com.jachwibangjeongsig.jb.property.repository;
import com.jachwibangjeongsig.jb.property.entity.Property;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findByLatBetweenAndLngBetweenOrderByCreatedAtDesc(
        double minLat, double maxLat, double minLng, double maxLng, Pageable pageable);

    /**
     * 추천 후보. 사각형은 통근 반경의 외접 박스라 실제 반경 밖 매물도 섞여 나오므로,
     * 호출부가 직선거리로 한 번 더 걸러야 한다.
     */
    @Query("""
        select p from Property p
        where p.lat between :minLat and :maxLat
          and p.lng between :minLng and :maxLng
          and p.deposit between :depositMin and :depositMax
          and p.monthlyRent between :monthlyRentMin and :monthlyRentMax
          and p.propertyType in :propertyTypes
        """)
    List<Property> findRecommendationCandidates(
        @Param("minLat") double minLat, @Param("maxLat") double maxLat,
        @Param("minLng") double minLng, @Param("maxLng") double maxLng,
        @Param("depositMin") int depositMin, @Param("depositMax") int depositMax,
        @Param("monthlyRentMin") int monthlyRentMin, @Param("monthlyRentMax") int monthlyRentMax,
        @Param("propertyTypes") List<String> propertyTypes);
}
