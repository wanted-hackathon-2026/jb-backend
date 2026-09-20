package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.favorite.entity.Favorite;
import com.jachwibangjeongsig.jb.favorite.repository.FavoriteRepository;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.PropertyImage;
import com.jachwibangjeongsig.jb.property.repository.PropertyImageRepository;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PropertyListingApiContractTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired MockMvc mvc;
	@Autowired PropertyRepository properties;
	@Autowired PropertyImageRepository images;
	@Autowired FavoriteRepository favorites;
	@Autowired UserRepository users;
	@Autowired JwtTokenService tokens;

	@BeforeEach
	void reset() {
		favorites.deleteAll();
		images.deleteAll();
		properties.deleteAll();
		users.deleteAll();
	}

	private Property saveProperty(double lat, double lng) {
		return properties.save(Property.builder()
			.name("테스트 매물").address("서울특별시 관악구 봉천동 919-19")
			.roadAddress("서울특별시 관악구 봉천로 1").sggCode("11620").umdName("봉천동")
			.lat(lat).lng(lng).propertyType("원룸").leaseType(LeaseType.MONTHLY)
			.deposit(3000).monthlyRent(45).build());
	}

	@Test
	void mapReturnsOnlyPropertiesInsideBoundsInclusive() throws Exception {
		saveProperty(37.50, 127.00);
		saveProperty(37.60, 127.00);
		saveProperty(38.00, 127.00);

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.50").param("maxLat", "37.60")
				.param("minLng", "126.90").param("maxLng", "127.10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties.length()").value(2));
	}

	@Test
	void mapReturnsThumbnailUrlOnlyWhenDisplayOrderZeroImageExists() throws Exception {
		Property withPhoto = saveProperty(37.50, 127.00);
		images.save(new PropertyImage(withPhoto, withPhoto.getId() + "/first.jpg", 0));
		saveProperty(37.51, 127.00);

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.0").param("maxLat", "38.0")
				.param("minLng", "126.0").param("maxLng", "128.0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[?(@.id=='" + withPhoto.getId() + "')].thumbnailUrl")
				.value("/api/property-images/" + withPhoto.getId() + "/first.jpg"));
	}

	@Test
	void mapRejectsInvalidBoundsAndMalformedParameters() throws Exception {
		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.60").param("maxLat", "37.50")
				.param("minLng", "126.90").param("maxLng", "127.10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MAP_BOUNDS"));

		mvc.perform(get("/api/properties/map")
				.param("minLat", "91").param("maxLat", "92")
				.param("minLng", "126.90").param("maxLng", "127.10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MAP_BOUNDS"));

		mvc.perform(get("/api/properties/map")
				.param("maxLat", "37.60").param("minLng", "126.90").param("maxLng", "127.10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.50").param("maxLat", "37.60")
				.param("minLng", "126.90").param("maxLng", "127.10").param("limit", "201"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void mapCapsAtLimitOrderedByMostRecentlyCreated() throws Exception {
		for (int i = 0; i < 3; i++) {
			saveProperty(37.50 + i * 0.001, 127.00);
		}

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.0").param("maxLat", "38.0")
				.param("minLng", "126.0").param("maxLng", "128.0").param("limit", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties.length()").value(2));
	}

	@Test
	void mapReturnsEmptyArrayWhenNothingMatches() throws Exception {
		mvc.perform(get("/api/properties/map")
				.param("minLat", "1").param("maxLat", "2")
				.param("minLng", "1").param("maxLng", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties.length()").value(0));
	}

	@Test
	void detailReturnsImagesOrderedByDisplayOrderOrEmptyArray() throws Exception {
		Property withPhotos = saveProperty(37.50, 127.00);
		images.save(new PropertyImage(withPhotos, withPhotos.getId() + "/b.jpg", 1));
		images.save(new PropertyImage(withPhotos, withPhotos.getId() + "/a.jpg", 0));
		Property withoutPhotos = saveProperty(37.51, 127.00);

		mvc.perform(get("/api/properties/{id}", withPhotos.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.images.length()").value(2))
			.andExpect(jsonPath("$.images[0].displayOrder").value(0))
			.andExpect(jsonPath("$.images[1].displayOrder").value(1));

		mvc.perform(get("/api/properties/{id}", withoutPhotos.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.images.length()").value(0));
	}

	@Test
	void detailReturns404ForUnknownProperty() throws Exception {
		mvc.perform(get("/api/properties/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));
	}

	@Test
	void detailWithoutAuthenticationReturnsFalseFavoriteWithoutRequiringLogin() throws Exception {
		Property property = saveProperty(37.50, 127.00);

		mvc.perform(get("/api/properties/{id}", property.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorite").value(false));
	}

	@Test
	void detailReflectsActualFavoriteStateForLoggedInUser() throws Exception {
		Property favorited = saveProperty(37.50, 127.00);
		Property notFavorited = saveProperty(37.51, 127.00);
		User user = users.save(User.builder().provider("google").providerId("viewer")
			.email("viewer@example.com").nickname("viewer").role(UserRole.USER).build());
		favorites.save(new Favorite(user.getId(), favorited));
		String bearer = "Bearer " + tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken();

		mvc.perform(get("/api/properties/{id}", favorited.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorite").value(true));

		mvc.perform(get("/api/properties/{id}", notFavorited.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.favorite").value(false));
	}

	@Test
	void detailReturnsSupplyAreaAndBathroomCountWhenPresentOrNullWhenAbsent() throws Exception {
		Property withDetails = properties.save(Property.builder()
			.name("테스트 매물").address("서울특별시 관악구 봉천동 919-19")
			.roadAddress("서울특별시 관악구 봉천로 1").sggCode("11620").umdName("봉천동")
			.lat(37.50).lng(127.00).propertyType("원룸").leaseType(LeaseType.MONTHLY)
			.deposit(3000).monthlyRent(45)
			.exclusiveArea(new BigDecimal("19.80")).supplyArea(new BigDecimal("33.00")).bathroomCount(1)
			.build());
		Property withoutDetails = saveProperty(37.51, 127.00);

		mvc.perform(get("/api/properties/{id}", withDetails.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplyArea").value(33.0))
			.andExpect(jsonPath("$.bathroomCount").value(1));

		mvc.perform(get("/api/properties/{id}", withoutDetails.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplyArea").value(nullValue()))
			.andExpect(jsonPath("$.bathroomCount").value(nullValue()));
	}

	@Test
	void mapReflectsActualFavoriteStatePerItemForLoggedInUser() throws Exception {
		Property favorited = saveProperty(37.50, 127.00);
		Property notFavorited = saveProperty(37.51, 127.00);
		User user = users.save(User.builder().provider("google").providerId("map-viewer")
			.email("map-viewer@example.com").nickname("mapviewer").role(UserRole.USER).build());
		favorites.save(new Favorite(user.getId(), favorited));
		String bearer = "Bearer " + tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken();

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.0").param("maxLat", "38.0")
				.param("minLng", "126.0").param("maxLng", "128.0")
				.header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[?(@.id=='" + favorited.getId() + "')].favorite").value(true))
			.andExpect(jsonPath("$.properties[?(@.id=='" + notFavorited.getId() + "')].favorite").value(false));

		mvc.perform(get("/api/properties/map")
				.param("minLat", "37.0").param("maxLat", "38.0")
				.param("minLng", "126.0").param("maxLng", "128.0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.properties[?(@.id=='" + favorited.getId() + "')].favorite").value(false));
	}
}
