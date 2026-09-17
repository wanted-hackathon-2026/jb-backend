package com.jachwibangjeongsig.jb.auth.config;

import com.jachwibangjeongsig.jb.auth.exception.ProfileIncompleteException;
import com.jachwibangjeongsig.jb.user.UserRepository;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ProfileAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final UserRepository users;

    public ProfileAuthorizationManager(UserRepository users) {
        this.users = users;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
        RequestAuthorizationContext context) {
        if (!(authentication.get() instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        UUID id;
        try {
            id = UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return new AuthorizationDecision(false);
        }
        var user = users.findById(id);
        if (user.isEmpty()) return new AuthorizationDecision(false);
        if (user.get().getNickname() == null) throw new ProfileIncompleteException();
        return new AuthorizationDecision(true);
    }
}
