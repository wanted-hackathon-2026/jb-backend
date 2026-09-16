package com.jachwibangjeongsig.jb.me;

import com.jachwibangjeongsig.jb.me.exception.ApiException;
import com.jachwibangjeongsig.jb.me.service.AccountServiceImpl;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountServiceErrorTest {
    @Test
    void nicknameUniqueConstraintIsTranslatedToConflict() {
        UserRepository users = repository();
        var violation = new DataIntegrityViolationException("duplicate",
            new ConstraintViolationException("duplicate", new SQLException(), "users.uk_users_nickname"));
        doThrow(violation).when(users).flush();
        assertThatThrownBy(() -> new AccountServiceImpl(users).updateNickname(UUID.randomUUID(), "새닉네임"))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("NICKNAME_ALREADY_EXISTS"));
    }

    @Test
    void unrelatedIntegrityFailureKeepsItsOriginalCause() {
        UserRepository users = repository();
        var violation = new DataIntegrityViolationException("unrelated constraint",
            new ConstraintViolationException("unrelated", new SQLException(), "uk_users_provider_provider_id"));
        doThrow(violation).when(users).flush();
        assertThatThrownBy(() -> new AccountServiceImpl(users).updateNickname(UUID.randomUUID(), "새닉네임"))
            .isSameAs(violation);
    }

    private UserRepository repository() {
        UserRepository users = mock(UserRepository.class);
        when(users.findById(any(UUID.class))).thenReturn(Optional.of(User.builder()
            .provider("google").providerId("test").email("test@example.com").build()));
        return users;
    }
}
