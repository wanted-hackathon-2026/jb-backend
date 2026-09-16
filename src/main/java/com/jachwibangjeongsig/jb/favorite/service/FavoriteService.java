package com.jachwibangjeongsig.jb.favorite.service;
import com.jachwibangjeongsig.jb.favorite.dto.FavoriteDtos.*;
import java.util.UUID;

public interface FavoriteService {
    Listing list(UUID userId, int page, int size);
    Detailed detail(UUID userId, UUID propertyId);
    Created register(UUID userId, UUID propertyId);
    void delete(UUID userId, UUID propertyId);
}
