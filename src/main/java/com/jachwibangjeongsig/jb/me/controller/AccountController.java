package com.jachwibangjeongsig.jb.me.controller;
import com.jachwibangjeongsig.jb.me.dto.*;
import com.jachwibangjeongsig.jb.me.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @GetMapping public AccountResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(UUID.fromString(jwt.getSubject()));
    }
    @PatchMapping public AccountResponse update(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody UpdateNicknameRequest request) {
        return service.updateNickname(UUID.fromString(jwt.getSubject()), request.nickname());
    }
}
