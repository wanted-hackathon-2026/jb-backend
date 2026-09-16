package com.jachwibangjeongsig.jb.me.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(@NotNull @Size(min = 2, max = 15) String nickname) {
    public UpdateNicknameRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
