package com.jachwibangjeongsig.jb.favorite.service;
import com.jachwibangjeongsig.jb.favorite.dto.FavoriteDtos.*;
import com.jachwibangjeongsig.jb.favorite.entity.Favorite;
import com.jachwibangjeongsig.jb.favorite.repository.FavoriteRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.me.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FavoriteServiceImpl implements FavoriteService {
    private final FavoriteRepository favorites;
    private final PropertyRepository properties;
    private final UserRepository users;
    public FavoriteServiceImpl(FavoriteRepository favorites, PropertyRepository properties, UserRepository users) {
        this.favorites = favorites; this.properties = properties; this.users = users;
    }
    public Listing list(UUID userId, int page, int size) {
        requireUser(userId);
        return Listing.from(favorites.findAllByUserId(userId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))));
    }
    public Detailed detail(UUID userId, UUID propertyId) {
        requireUser(userId);
        return Detailed.from(favorites.findByUserIdAndPropertyId(userId, propertyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAVORITE_NOT_FOUND", "즐겨찾기를 찾을 수 없습니다.")));
    }
    @Transactional
    public Created register(UUID userId, UUID propertyId) {
        // Serialize registrations for this user; DB uniqueness remains a second line of defense.
        users.findByIdForUpdate(userId).orElseThrow(this::unauthorized);
        var property = properties.findById(propertyId).orElseThrow(() ->
            new ApiException(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "매물을 찾을 수 없습니다."));
        if (favorites.existsByUserIdAndPropertyId(userId, propertyId)) {
            throw new ApiException(HttpStatus.CONFLICT, "FAVORITE_ALREADY_EXISTS", "이미 등록된 즐겨찾기입니다.");
        }
        return Created.from(favorites.saveAndFlush(new Favorite(userId, property)));
    }
    @Transactional
    public void delete(UUID userId, UUID propertyId) {
        requireUser(userId);
        favorites.deleteByUserIdAndPropertyId(userId, propertyId);
    }
    private void requireUser(UUID id) { if (!users.existsById(id)) throw unauthorized(); }
    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", "인증에 실패했습니다.");
    }
}
