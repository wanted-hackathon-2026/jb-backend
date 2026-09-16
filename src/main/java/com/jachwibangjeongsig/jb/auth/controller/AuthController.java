package com.jachwibangjeongsig.jb.auth.controller;

import com.jachwibangjeongsig.jb.auth.config.AuthProperties;
import com.jachwibangjeongsig.jb.auth.dto.GoogleLoginRequest;
import com.jachwibangjeongsig.jb.auth.dto.LoginResponse;
import com.jachwibangjeongsig.jb.auth.dto.TokenResponse;
import com.jachwibangjeongsig.jb.auth.service.AuthService;
import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.auth.exception.InvalidRefreshTokenException;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@SecurityRequirements
public class AuthController {

	private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

	private final AuthService authService;
	private final AuthProperties authProperties;

	public AuthController(AuthService authService, AuthProperties authProperties) {
		this.authService = authService;
		this.authProperties = authProperties;
	}

	@PostMapping("/api/auth/login/google")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody GoogleLoginRequest request) {
		AuthService.LoginResult result = authService.loginWithGoogle(request.idToken());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie(result.tokens().refreshToken()).toString())
			.body(LoginResponse.from(result));
	}

	@PostMapping("/api/auth/reissue")
	public ResponseEntity<TokenResponse> reissue(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
	) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		AuthService.ReissueResult result = authService.reissue(refreshToken);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie(result.tokens().refreshToken()).toString())
			.body(TokenResponse.from(result.tokens()));
	}

	@PostMapping("/api/logout")
	public ResponseEntity<Void> logout(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
		HttpServletResponse response
	) {
		authService.logout(refreshToken);
		response.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	private ResponseCookie refreshCookie(String value) {
		return cookie(value, JwtTokenService.REFRESH_TOKEN_TTL);
	}

	private ResponseCookie expiredRefreshCookie() {
		return cookie("", Duration.ZERO);
	}

	private ResponseCookie cookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
			.httpOnly(true)
			.secure(authProperties.cookieSecure())
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

}
