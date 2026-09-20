package com.jachwibangjeongsig.jb.me.service;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import com.jachwibangjeongsig.jb.me.dto.AccountResponse;
import com.jachwibangjeongsig.jb.me.exception.ApiException;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {
    private final UserRepository users;
    public AccountServiceImpl(UserRepository users) { this.users = users; }
    public AccountResponse get(UUID id) { return AccountResponse.from(requireUser(id)); }
    @Transactional
    public AccountResponse updateNickname(UUID id, String nickname) {
        User user = requireUser(id);
        if (users.existsByNicknameAndIdNot(nickname, id)) throw duplicate();
        user.updateNickname(nickname);
        try { users.flush(); }
        catch (DataIntegrityViolationException exception) {
            if (isNicknameConflict(exception)) throw duplicate();
            throw exception;
        }
        return AccountResponse.from(user);
    }
    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
            "INVALID_ACCESS_TOKEN", "인증에 실패했습니다."));
    }
    private ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.");
    }
    private boolean isNicknameConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if (constraint != null && (constraint.equalsIgnoreCase("uk_users_nickname")
                    || constraint.toLowerCase(Locale.ROOT).endsWith(".uk_users_nickname"))) return true;
            }
        }
        return false;
    }
}
