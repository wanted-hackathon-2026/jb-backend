package com.jachwibangjeongsig.jb.favorite.controller;
import com.jachwibangjeongsig.jb.favorite.dto.FavoriteDtos.*;
import com.jachwibangjeongsig.jb.favorite.service.FavoriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {
    private final FavoriteService service;
    public FavoriteController(FavoriteService service) { this.service = service; }
    @GetMapping public Listing list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(UUID.fromString(jwt.getSubject()), page, size);
    }
    @GetMapping("/{propertyId}") public Detailed detail(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID propertyId) {
        return service.detail(UUID.fromString(jwt.getSubject()), propertyId);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Created register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Register request) {
        return service.register(UUID.fromString(jwt.getSubject()), request.propertyId());
    }
    @DeleteMapping("/{propertyId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID propertyId) {
        service.delete(UUID.fromString(jwt.getSubject()), propertyId);
    }
}
