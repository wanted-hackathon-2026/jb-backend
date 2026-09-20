package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key",
	"property.image-directory=build/test-property-images"
})
@AutoConfigureMockMvc
@Testcontainers
class PropertyImageApiContractTest {

	private static final Path IMAGE_DIRECTORY = Path.of("build/test-property-images");
	private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
	private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired PropertyRepository properties;
	@Autowired UserRepository users;
	@Autowired JwtTokenService tokens;
	@Autowired JdbcTemplate jdbc;

	private User admin;
	private User ordinary;
	private Property property;

	@BeforeEach
	void reset() throws Exception {
		if (Files.exists(IMAGE_DIRECTORY)) {
			try (var paths = Files.walk(IMAGE_DIRECTORY)) {
				paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(IMAGE_DIRECTORY))
					.forEach(path -> path.toFile().delete());
			}
		}
		properties.deleteAll();
		users.deleteAll();
		admin = saveUser("admin", UserRole.ADMIN);
		ordinary = saveUser("ordinary", UserRole.USER);
		property = properties.save(Property.builder()
			.name("테스트 매물").address("서울특별시 관악구 봉천동 919-19")
			.roadAddress("서울특별시 관악구 봉천로 1").sggCode("11620").umdName("봉천동")
			.lat(37.0).lng(127.0).propertyType("원룸").leaseType(LeaseType.MONTHLY)
			.deposit(3000).monthlyRent(45).build());
	}

	@Test
	void adminUploadsImagesInRequestOrderAndCanReadThem() throws Exception {
		var result = mvc.perform(multipart("/api/properties/{id}/images", property.getId())
			.file(image("first.png")).file(image("second.png"))
			.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.propertyId").value(property.getId().toString()))
			.andExpect(jsonPath("$.images[0].displayOrder").value(0))
			.andExpect(jsonPath("$.images[1].displayOrder").value(1))
			.andExpect(jsonPath("$.images[0].url").isNotEmpty())
			.andReturn();

		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_image WHERE property_id = UUID_TO_BIN(?)",
			Long.class, property.getId().toString())).isEqualTo(2);
		try (var paths = Files.list(IMAGE_DIRECTORY.resolve(property.getId().toString()))) {
			assertThat(paths).hasSize(2);
		}

		mvc.perform(get(body.path("images").get(0).path("url").asString()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"));
	}

	@Test
	void laterUploadAppendsAfterExistingImages() throws Exception {
		upload(admin, property.getId(), image("first.png")).andExpect(status().isCreated());
		upload(admin, property.getId(), image("second.png"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.images[0].displayOrder").value(1));
	}

	@Test
	void adminReplacesImageWhileKeepingItsIdAndOrder() throws Exception {
		JsonNode uploaded = response(upload(admin, property.getId(), image("first.png"))
			.andExpect(status().isCreated()));
		JsonNode original = uploaded.path("images").get(0);
		UUID imageId = UUID.fromString(original.path("id").asString());
		String originalUrl = original.path("url").asString();

		var request = multipart("/api/properties/{propertyId}/images/{imageId}", property.getId(), imageId)
			.file(new MockMultipartFile("file", "replacement.jpg", "image/jpeg", JPEG))
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.with(httpRequest -> {
				httpRequest.setMethod("PUT");
				return httpRequest;
			});
		mvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(imageId.toString()))
			.andExpect(jsonPath("$.displayOrder").value(0))
			.andExpect(jsonPath("$.url").value(endsWith(".jpg")));

		mvc.perform(get(originalUrl)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_image", Long.class)).isOne();
		try (var paths = Files.list(IMAGE_DIRECTORY.resolve(property.getId().toString()))) {
			assertThat(paths).hasSize(1);
		}
	}

	@Test
	void adminDeletesImageAndCompactsFollowingDisplayOrders() throws Exception {
		JsonNode uploaded = response(upload(admin, property.getId(), image("first.png"), image("second.png"),
			image("third.png")).andExpect(status().isCreated()));
		UUID middleImageId = UUID.fromString(uploaded.path("images").get(1).path("id").asString());
		String deletedUrl = uploaded.path("images").get(1).path("url").asString();

		mvc.perform(delete("/api/properties/{propertyId}/images/{imageId}", property.getId(), middleImageId)
			.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isNoContent());

		mvc.perform(get(deletedUrl)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForList("SELECT display_order FROM property_image WHERE property_id = UUID_TO_BIN(?) "
			+ "ORDER BY display_order", Integer.class, property.getId().toString())).containsExactly(0, 1);
		try (var paths = Files.list(IMAGE_DIRECTORY.resolve(property.getId().toString()))) {
			assertThat(paths).hasSize(2);
		}
	}

	@Test
	void replaceAndDeleteRequireAdminAndMatchingPropertyImage() throws Exception {
		JsonNode uploaded = response(upload(admin, property.getId(), image("first.png"))
			.andExpect(status().isCreated()));
		UUID imageId = UUID.fromString(uploaded.path("images").get(0).path("id").asString());

		mvc.perform(delete("/api/properties/{propertyId}/images/{imageId}", property.getId(), imageId))
			.andExpect(status().isUnauthorized());
		mvc.perform(delete("/api/properties/{propertyId}/images/{imageId}", property.getId(), imageId)
			.header(HttpHeaders.AUTHORIZATION, bearer(ordinary)))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/properties/{propertyId}/images/{imageId}", property.getId(), UUID.randomUUID())
			.header(HttpHeaders.AUTHORIZATION, bearer(admin)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_IMAGE_NOT_FOUND"));
	}

	@Test
	void jpegAndWebpAreDetectedFromContentAndServedWithStoredTypes() throws Exception {
		var result = upload(admin, property.getId(),
			new MockMultipartFile("files", "wrong.bin", "application/octet-stream", JPEG),
			new MockMultipartFile("files", "wrong.bin", "application/octet-stream", WEBP))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.images[0].url").value(endsWith(".jpg")))
			.andExpect(jsonPath("$.images[1].url").value(endsWith(".webp")))
			.andReturn();

		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		mvc.perform(get(body.path("images").get(0).path("url").asString()))
			.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
		mvc.perform(get(body.path("images").get(1).path("url").asString()))
			.andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"));
	}

	@Test
	void databaseFailureReturnsSpecifiedErrorAndRemovesWrittenFile() throws Exception {
		jdbc.execute("""
			ALTER TABLE property_image ADD CONSTRAINT chk_forced_property_image_failure
			CHECK (storage_key = 'forced-impossible-value')
			""");
		try {
			upload(admin, property.getId(), image("room.png"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("PROPERTY_IMAGE_STORAGE_FAILED"));
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_image", Long.class)).isZero();
			try (var paths = Files.list(IMAGE_DIRECTORY.resolve(property.getId().toString()))) {
				assertThat(paths).isEmpty();
			}
		} finally {
			jdbc.execute("ALTER TABLE property_image DROP CHECK chk_forced_property_image_failure");
		}
	}

	@Test
	void authenticationRoleAndPropertyExistenceAreEnforced() throws Exception {
		mvc.perform(multipart("/api/properties/{id}/images", property.getId()).file(image("room.png")))
			.andExpect(status().isUnauthorized());
		upload(ordinary, property.getId(), image("room.png")).andExpect(status().isForbidden());
		upload(admin, UUID.randomUUID(), image("room.png"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@Test
	void emptyUnsupportedOversizedAndTooManyFilesAreRejected() throws Exception {
		upload(admin, property.getId(), new MockMultipartFile("files", "empty.png", "image/png", new byte[0]))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PROPERTY_IMAGE"));
		upload(admin, property.getId(), new MockMultipartFile("files", "fake.png", "image/png", "text".getBytes()))
			.andExpect(status().isUnsupportedMediaType())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_PROPERTY_IMAGE_TYPE"));
		upload(admin, property.getId(), new MockMultipartFile("files", "large.png", "image/png",
			new byte[10 * 1024 * 1024 + 1]))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("PROPERTY_IMAGE_TOO_LARGE"));

		var request = multipart("/api/properties/{id}/images", property.getId())
			.header(HttpHeaders.AUTHORIZATION, bearer(admin));
		for (int index = 0; index < 11; index++) {
			request.file(image(index + ".png"));
		}
		mvc.perform(request).andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_image", Long.class)).isZero();
	}

	private ResultActions upload(User user, UUID propertyId,
		MockMultipartFile... files) throws Exception {
		var request = multipart("/api/properties/{id}/images", propertyId)
			.header(HttpHeaders.AUTHORIZATION, bearer(user));
		for (MockMultipartFile file : files) {
			request.file(file);
		}
		return mvc.perform(request);
	}

	private MockMultipartFile image(String name) {
		return new MockMultipartFile("files", name, "image/png", PNG);
	}

	private JsonNode response(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken();
	}

	private User saveUser(String name, UserRole role) {
		return users.save(User.builder().provider("google").providerId(name).email(name + "@example.com")
			.nickname(name).role(role).build());
	}
}
