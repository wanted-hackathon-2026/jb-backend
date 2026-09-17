package com.jachwibangjeongsig.jb.auth.exception;

import org.springframework.security.access.AccessDeniedException;

public class ProfileIncompleteException extends AccessDeniedException {
    public ProfileIncompleteException() {
        super("닉네임을 설정해 가입을 완료해 주세요.");
    }
}
