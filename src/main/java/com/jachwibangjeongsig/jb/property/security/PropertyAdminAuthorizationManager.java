package com.jachwibangjeongsig.jb.property.security;

import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class PropertyAdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private final UserRepository userRepository;

	public PropertyAdminAuthorizationManager(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
		RequestAuthorizationContext context) {
		Authentication current = authentication.get();
		if (!(current instanceof JwtAuthenticationToken jwt) || !current.isAuthenticated()) {
			return new AuthorizationDecision(false);
		}
		UUID userId;
		try {
			userId = UUID.fromString(jwt.getToken().getSubject());
		} catch (IllegalArgumentException | NullPointerException exception) {
			return new AuthorizationDecision(false);
		}
		// Read the current database role, not the potentially stale JWT role claim.
		boolean admin = userRepository.findById(userId)
			.map(user -> user.getRole() == UserRole.ADMIN)
			.orElse(false);
		return new AuthorizationDecision(admin);
	}
}
