package com.jachwibangjeongsig.jb.favorite.dto;
import com.jachwibangjeongsig.jb.favorite.entity.Favorite;
import com.jachwibangjeongsig.jb.property.entity.Property;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class FavoriteDtos {
    private FavoriteDtos() {}
    public record Register(@NotNull UUID propertyId) {}
    public record Created(UUID favoriteId, UUID propertyId, LocalDateTime createdAt) {
        public static Created from(Favorite f) { return new Created(f.getId(), f.getProperty().getId(), f.getCreatedAt()); }
    }
    public record Summary(UUID id, String name, String address, String roadAddress, String propertyType,
                          int deposit, int monthlyRent, BigDecimal exclusiveArea, Integer floor, Integer buildYear) {
        public static Summary from(Property p) {
            return new Summary(p.getId(), p.getName(), p.getAddress(), p.getRoadAddress(), p.getPropertyType(),
                p.getDeposit(), p.getMonthlyRent(), p.getExclusiveArea(), p.getFloor(), p.getBuildYear());
        }
    }
    public record Detail(UUID id, String name, String address, String roadAddress, String sggCode,
                         String umdName, double latitude, double longitude, String propertyType,
                         int deposit, int monthlyRent, BigDecimal exclusiveArea, Integer floor,
                         Integer totalFloors, Integer buildYear, String direction, String description) {
        public static Detail from(Property p) {
            return new Detail(p.getId(), p.getName(), p.getAddress(), p.getRoadAddress(), p.getSggCode(),
                p.getUmdName(), p.getLat(), p.getLng(), p.getPropertyType(), p.getDeposit(), p.getMonthlyRent(),
                p.getExclusiveArea(), p.getFloor(), p.getTotalFloors(), p.getBuildYear(), p.getDirection(), p.getDescription());
        }
    }
    public record Item(UUID favoriteId, LocalDateTime createdAt, Summary property) {
        public static Item from(Favorite f) { return new Item(f.getId(), f.getCreatedAt(), Summary.from(f.getProperty())); }
    }
    public record Detailed(UUID favoriteId, LocalDateTime createdAt, Detail property) {
        public static Detailed from(Favorite f) { return new Detailed(f.getId(), f.getCreatedAt(), Detail.from(f.getProperty())); }
    }
    public record Listing(List<Item> content, int page, int size, long totalElements, int totalPages, boolean last) {
        public static Listing from(Page<Favorite> p) {
            return new Listing(p.getContent().stream().map(Item::from).toList(), p.getNumber(),
                p.getSize(), p.getTotalElements(), p.getTotalPages(), p.isLast());
        }
    }
}
