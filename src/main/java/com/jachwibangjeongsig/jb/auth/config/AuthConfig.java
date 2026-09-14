package com.jachwibangjeongsig.jb.auth.config;

import com.jachwibangjeongsig.jb.auth.exception.AuthAuthenticationEntryPoint;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		JwtDecoder accessTokenDecoder,
		AuthenticationEntryPoint authenticationEntryPoint
	) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST,
					"/api/auth/login/google",
					"/api/auth/reissue",
					"/api/logout"
				).permitAll()
				.requestMatchers("/actuator/health/**").permitAll()
				.anyRequest().authenticated()
			)
			.oauth2ResourceServer(oauth -> oauth
				.jwt(jwt -> jwt.decoder(accessTokenDecoder))
				.authenticationEntryPoint(authenticationEntryPoint)
			);
		return http.build();
	}

	@Bean
	JwtDecoder accessTokenDecoder(AuthProperties properties) {
		SecretKeySpec key = new SecretKeySpec(
			properties.accessTokenSecret().getBytes(StandardCharsets.UTF_8),
			"HmacSHA256"
		);
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
		OAuth2TokenValidator<Jwt> tokenType = jwt -> "access".equals(jwt.getClaimAsString("token_type"))
			? OAuth2TokenValidatorResult.success()
			: OAuth2TokenValidatorResult.failure(
				new OAuth2Error("invalid_token", "Invalid token type", null)
			);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			JwtValidators.createDefault(),
			tokenType
		));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
