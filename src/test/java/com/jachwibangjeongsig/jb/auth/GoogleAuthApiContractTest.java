package com.jachwibangjeongsig.jb.auth;

import com.jachwibangjeongsig.jb.auth.dto.GoogleIdentity;
import com.jachwibangjeongsig.jb.auth.exception.InvalidGoogleIdentityTokenException;
import com.jachwibangjeongsig.jb.auth.service.GoogleIdentityVerifier;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"auth.cookie-secure=true",
	"auth.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(GoogleAuthApiContractTest.FakeGoogleIdentityConfiguration.class)
class GoogleAuthApiContractTest {

	private static final String NEW_USER_TOKEN = "valid-new-user-token";
	private static final String EXISTING_USER_TOKEN = "valid-existing-user-token";
	private static final String INVALID_TOKEN = "invalid-token";

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void firstGoogleLoginCreatesUserAndIssuesTokens() throws Exception {
		MvcResult result = login(NEW_USER_TOKEN)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
			.andExpect(cookie().exists("refresh_token"))
			.andExpect(cookie().httpOnly("refresh_token", true))
			.andExpect(cookie().secure("refresh_token", true))
			.andExpect(cookie().sameSite("refresh_token", "Lax"))
			.andExpect(cookie().path("refresh_token", "/"))
			.andExpect(cookie().maxAge("refresh_token", 1_209_600))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.isNewUser").value(true))
			.andExpect(jsonPath("$.user.email").value("new-user@example.com"))
			.andExpect(jsonPath("$.user.nickname").value((Object) null))
			.andExpect(jsonPath("$.user.profileCompleted").value(false))
			.andReturn();

		List<User> users = userRepository.findAll();
		assertThat(users).hasSize(1);
		User user = users.getFirst();
		assertThat(user.getProvider()).isEqualTo("google");
		assertThat(user.getProviderId()).isEqualTo("google-sub-new");
		assertThat(user.getEmail()).isEqualTo("new-user@example.com");
		assertThat(user.getNickname()).isNull();
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertAccessToken(result, user);
		assertRefreshToken(result, user);
	}

	@Test
	void concurrentFirstLoginsCreateExactlyOneUserAndBothSucceed() throws Exception {
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			java.util.concurrent.Callable<JsonNode> request = () -> {
				assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				return objectMapper.readTree(login(NEW_USER_TOKEN)
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
			};
			var first = executor.submit(request);
			var second = executor.submit(request);
			start.countDown();
			JsonNode a = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
			JsonNode b = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
			assertThat(a.get("user").get("id").asText()).isEqualTo(b.get("user").get("id").asText());
			assertThat(java.util.List.of(a.get("isNewUser").asBoolean(), b.get("isNewUser").asBoolean()))
				.containsExactlyInAnyOrder(true, false);
			assertThat(userRepository.count()).isEqualTo(1);
		}
	}

	@Test
	void returningGoogleLoginKeepsIdentityAndNicknameButUpdatesEmail() throws Exception {
		User existingUser = userRepository.save(User.builder()
			.provider("google")
			.providerId("google-sub-existing")
			.email("old-email@example.com")
			.nickname("직접정한닉네임")
			.role(UserRole.ADMIN)
			.build());

		login(EXISTING_USER_TOKEN)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isNewUser").value(false))
			.andExpect(jsonPath("$.user.id").value(existingUser.getId().toString()))
			.andExpect(jsonPath("$.user.email").value("new-email@example.com"))
			.andExpect(jsonPath("$.user.nickname").value("직접정한닉네임"))
			.andExpect(jsonPath("$.user.profileCompleted").value(true));

		List<User> users = userRepository.findAll();
		assertThat(users).hasSize(1);
		User reloaded = users.getFirst();
		assertThat(reloaded.getId()).isEqualTo(existingUser.getId());
		assertThat(reloaded.getEmail()).isEqualTo("new-email@example.com");
		assertThat(reloaded.getNickname()).isEqualTo("직접정한닉네임");
		assertThat(reloaded.getRole()).isEqualTo(UserRole.ADMIN);
	}

	@Test
	void missingIdTokenReturnsProblemDetails() throws Exception {
		mockMvc.perform(post("/api/auth/login/google")
				.contentType(APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void invalidGoogleTokenDoesNotCreateUserOrIssueRefreshToken() throws Exception {
		login(INVALID_TOKEN)
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(cookie().doesNotExist("refresh_token"))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"))
			.andExpect(jsonPath("$.instance").value("/api/auth/login/google"));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void reissueRotatesRefreshTokenAndRejectsThePreviousToken() throws Exception {
		Cookie original = refreshCookie(login(NEW_USER_TOKEN).andReturn());

		MvcResult reissue = mockMvc.perform(post("/api/auth/reissue").cookie(original))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(cookie().exists("refresh_token"))
			.andReturn();

		Cookie rotated = refreshCookie(reissue);
		assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

		mockMvc.perform(post("/api/auth/reissue").cookie(original))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void reissueWithoutRefreshTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/auth/reissue"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutRevokesCurrentRefreshTokenAndIsIdempotent() throws Exception {
		Cookie refreshToken = refreshCookie(login(NEW_USER_TOKEN).andReturn());

		mockMvc.perform(post("/api/logout").cookie(refreshToken))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""))
			.andExpect(cookie().maxAge("refresh_token", 0));

		mockMvc.perform(post("/api/auth/reissue").cookie(refreshToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

		mockMvc.perform(post("/api/logout").cookie(refreshToken))
			.andExpect(status().isNoContent());
	}

	@Test
	void logoutWithoutRefreshTokenIsStillSuccessful() throws Exception {
		mockMvc.perform(post("/api/logout"))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""))
			.andExpect(cookie().maxAge("refresh_token", 0));
	}

	private org.springframework.test.web.servlet.ResultActions login(String idToken) throws Exception {
		return mockMvc.perform(post("/api/auth/login/google")
			.contentType(APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("idToken", idToken))));
	}

	private Cookie refreshCookie(MvcResult result) {
		Cookie cookie = result.getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	private void assertAccessToken(MvcResult result, User user) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode claims = jwtClaims(response.get("accessToken").asText());
		assertThat(claims.get("sub").asText()).isEqualTo(user.getId().toString());
		assertThat(claims.get("role").asText()).isEqualTo("USER");
		assertThat(claims.get("token_type").asText()).isEqualTo("access");
		assertThat(Duration.ofSeconds(claims.get("exp").asLong() - claims.get("iat").asLong()))
			.isEqualTo(Duration.ofMinutes(15));
	}

	private void assertRefreshToken(MvcResult result, User user) throws Exception {
		JsonNode claims = jwtClaims(refreshCookie(result).getValue());
		assertThat(claims.get("sub").asText()).isEqualTo(user.getId().toString());
		assertThat(claims.get("jti").asText()).isNotBlank();
		assertThat(claims.get("token_type").asText()).isEqualTo("refresh");
		assertThat(Duration.ofSeconds(claims.get("exp").asLong() - claims.get("iat").asLong()))
			.isEqualTo(Duration.ofDays(14));
	}

	private JsonNode jwtClaims(String jwt) throws Exception {
		String[] parts = jwt.split("\\.");
		assertThat(parts).hasSize(3);
		byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
		return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeGoogleIdentityConfiguration {

		@Bean
		@Primary
		GoogleIdentityVerifier fakeGoogleIdentityVerifier() {
			Map<String, GoogleIdentity> identities = Map.of(
				NEW_USER_TOKEN, new GoogleIdentity("google-sub-new", "new-user@example.com"),
				EXISTING_USER_TOKEN, new GoogleIdentity("google-sub-existing", "new-email@example.com")
			);
			return idToken -> {
				GoogleIdentity identity = identities.get(idToken);
				if (identity == null) {
					throw new InvalidGoogleIdentityTokenException();
				}
				return identity;
			};
		}
	}
}
