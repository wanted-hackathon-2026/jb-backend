package com.jachwibangjeongsig.jb.me.dto;
import com.jachwibangjeongsig.jb.user.User;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public record AccountResponse(UUID id, String provider, String email, String nickname,
                              String role, boolean profileCompleted, LocalDateTime createdAt) {
    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getProvider().toUpperCase(Locale.ROOT),
            user.getEmail(), user.getNickname(), user.getRole().name(),
            user.getNickname() != null, user.getCreatedAt());
    }
}
