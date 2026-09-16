package com.jachwibangjeongsig.jb.me.service;
import com.jachwibangjeongsig.jb.me.dto.AccountResponse;
import java.util.UUID;

public interface AccountService {
    AccountResponse get(UUID userId);
    AccountResponse updateNickname(UUID userId, String nickname);
}
