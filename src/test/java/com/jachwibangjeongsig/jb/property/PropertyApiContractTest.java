package com.jachwibangjeongsig.jb.property;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingUnavailableException;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.property.service.CsvSafetyFacilitySource;
import com.jachwibangjeongsig.jb.property.service.NoiseMetricService;
import com.jachwibangjeongsig.jb.property.service.NoiseObservationSource;
import com.jachwibangjeongsig.jb.property.service.SafetyFacilitySource;
import com.jachwibangjeongsig.jb.property.service.SafetyFacilitySource.Kind;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricCalculator.Facility;
import com.jachwibangjeongsig.jb.property.service.SafetyMetricService;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(PropertyApiContractTest.StubGeocodingConfiguration.class)
class PropertyApiContractTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	private static final Coordinates LOCATION = new Coordinates(37.279609852101984, 127.04339808904444);

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired PropertyRepository properties;
	@Autowired UserRepository users;
	@Autowired JwtTokenService tokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired StubGeocodingClient geocoding;
	@Autowired StubSafetySource safetySource;
	@Autowired SafetyMetricService safetyMetrics;
	@Autowired StubNoiseSource noiseSource;
	@Autowired NoiseMetricService noiseMetrics;

	private User admin;
	private User ordinary;

	@BeforeEach
	void reset() {
		noiseSource.enabled = false;
		noiseSource.hours = 84;
		noiseSource.fail = false;
		noiseSource.value = "50";
		noiseSource.sensorId = "test-sensor";
		properties.deleteAll();
		users.deleteAll();
		geocoding.calls = 0;
		geocoding.address = null;
		geocoding.result = Optional.of(LOCATION);
		geocoding.failure = null;
		safetySource.failure = null;
		safetySource.calls = 0;
		safetySource.empty = false;
		safetySource.unavailable = false;
		safetySource.multiplier = 1;
		safetySource.delegate = null;
		admin = saveUser("admin", UserRole.ADMIN);
		ordinary = saveUser("ordinary", UserRole.USER);
	}

	@Test
	void adminCreatesPropertyWithServerCoordinatesAndAuditing() throws Exception {
		Map<String, Object> body = minimalBody();
		body.put("exclusiveArea", new BigDecimal("23.50"));
		body.put("floor", 3);
		body.put("totalFloors", 10);
		body.put("buildYear", 2020);
		body.put("direction", "남향");
		body.put("description", "매물 설명");
		var response = create(bearer(admin), body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("테스트 매물"))
			.andExpect(jsonPath("$.address").value(body.get("address")))
			.andExpect(jsonPath("$.roadAddress").value(body.get("roadAddress")))
			.andExpect(jsonPath("$.sggCode").value("41115"))
			.andExpect(jsonPath("$.umdName").value("우만동"))
			.andExpect(jsonPath("$.propertyType").value("원룸"))
			.andExpect(jsonPath("$.leaseType").value("MONTHLY"))
			.andExpect(jsonPath("$.deposit").value(1000))
			.andExpect(jsonPath("$.monthlyRent").value(50))
			.andExpect(jsonPath("$.exclusiveArea").value(23.5))
			.andExpect(jsonPath("$.floor").value(3))
			.andExpect(jsonPath("$.totalFloors").value(10))
			.andExpect(jsonPath("$.buildYear").value(2020))
			.andExpect(jsonPath("$.direction").value("남향"))
			.andExpect(jsonPath("$.description").value("매물 설명"))
			.andExpect(jsonPath("$.latitude").value(LOCATION.lat()))
			.andExpect(jsonPath("$.longitude").value(LOCATION.lng()))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.updatedAt").isNotEmpty())
			.andReturn();

		UUID id = UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("id").asString());
		Property saved = properties.findById(id).orElseThrow();
		assertThat(properties.count()).isEqualTo(1);
		assertThat(saved.getName()).isEqualTo("테스트 매물");
		assertThat(saved.getLeaseType()).isEqualTo(LeaseType.MONTHLY);
		assertThat(saved.getExclusiveArea()).isEqualByComparingTo("23.50");
		assertThat(saved.getLat()).isEqualTo(LOCATION.lat());
		assertThat(saved.getLng()).isEqualTo(LOCATION.lng());
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(geocoding.calls).isEqualTo(1);
		assertThat(geocoding.address).isEqualTo(body.get("roadAddress"));
	}

	@Test
	void optionalFieldsCanBeOmittedForJeonseWithZeroMonthlyRent() throws Exception {
		Map<String, Object> body = minimalBody();
		body.put("deposit", 0);
		body.put("monthlyRent", 0);
		body.put("leaseType", "JEONSE");
		create(bearer(admin), body).andExpect(status().isCreated());
		Property saved = properties.findAll().getFirst();
		assertThat(saved.getLeaseType()).isEqualTo(LeaseType.JEONSE);
		assertThat(saved.getDeposit()).isZero();
		assertThat(saved.getMonthlyRent()).isZero();
		assertThat(saved.getExclusiveArea()).isNull();
		assertThat(saved.getFloor()).isNull();
		assertThat(saved.getTotalFloors()).isNull();
		assertThat(saved.getBuildYear()).isNull();
		assertThat(saved.getDirection()).isNull();
		assertThat(saved.getDescription()).isNull();
	}

	@Test
	void acceptsBasementAndBoundaryValuesWithoutRestrictingTypeOrDirection() throws Exception {
		Map<String, Object> body = minimalBody();
		body.put("name", "가".repeat(100));
		body.put("address", "가".repeat(255));
		body.put("roadAddress", "가".repeat(255));
		body.put("sggCode", "41115");
		body.put("umdName", "가".repeat(50));
		body.put("propertyType", "가".repeat(20));
		body.put("direction", "가".repeat(10));
		body.put("description", "가".repeat(16383));
		body.put("exclusiveArea", new BigDecimal("999999.99"));
		body.put("floor", -1);
		create(bearer(admin), body).andExpect(status().isCreated());
		assertThat(properties.findAll().getFirst().getFloor()).isEqualTo(-1);
	}

	@Test
	void repeatedRegistrationCreatesDifferentProperties() throws Exception {
		create(bearer(admin), minimalBody()).andExpect(status().isCreated());
		create(bearer(admin), minimalBody()).andExpect(status().isCreated());
		assertThat(properties.findAll()).hasSize(2)
			.extracting(Property::getId).doesNotHaveDuplicates();
	}

	@Test
	void unauthenticatedRequestsAreRejectedBeforeValidation() throws Exception {
		mvc.perform(post("/api/properties").contentType(APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
		assertNoWork();
	}

	@Test
	void expiredAndTamperedTokensAreRejected() throws Exception {
		String expired = "Bearer " + tokens.issue(admin, UUID.randomUUID(), Instant.now().minusSeconds(1800)).accessToken();
		create(expired, minimalBody()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
		String valid = bearer(admin);
		int signatureStart = valid.lastIndexOf('.') + 1;
		char replacement = valid.charAt(signatureStart) == 'A' ? 'B' : 'A';
		String tampered = valid.substring(0, signatureStart) + replacement + valid.substring(signatureStart + 1);
		create(tampered, minimalBody()).andExpect(status().isUnauthorized());
		assertNoWork();
	}

	@Test
	void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
		String refresh = tokens.issue(admin, UUID.randomUUID(), Instant.now()).refreshToken();
		create("Bearer " + refresh, minimalBody()).andExpect(status().isUnauthorized());
		assertNoWork();
	}

	@Test
	void ordinaryUsersAreForbiddenEvenForInvalidBody() throws Exception {
		create(bearer(ordinary), Map.of())
			.andExpect(status().isForbidden())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.code").value("FORBIDDEN"))
			.andExpect(jsonPath("$.instance").value("/api/properties"))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
		assertNoWork();
	}

	@Test
	void revokedAdminRoleTakesEffectWithoutReissuingToken() throws Exception {
		String token = bearer(admin);
		changeRole(admin, UserRole.USER);
		create(token, minimalBody()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertNoWork();
	}

	@Test
	void newlyGrantedAdminRoleTakesEffectWithoutReissuingToken() throws Exception {
		String token = bearer(ordinary);
		changeRole(ordinary, UserRole.ADMIN);
		create(token, minimalBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
	}

	@Test
	void deletedUsersCannotRegisterUsingExistingTokens() throws Exception {
		String token = bearer(admin);
		users.deleteById(admin.getId());
		create(token, minimalBody()).andExpect(status().isForbidden());
		assertNoWork();
	}

	@ParameterizedTest
	@MethodSource("invalidRequiredValues")
	void requiredFieldsRejectMissingNullAndBlank(String field, String kind) throws Exception {
		Map<String, Object> body = minimalBody();
		switch (kind) {
			case "missing" -> body.remove(field);
			case "null" -> body.put(field, null);
			case "empty" -> body.put(field, "");
			case "blank" -> body.put(field, " \t\n");
			default -> throw new IllegalArgumentException(kind);
		}
		assertInvalid(body);
	}

	static Stream<Arguments> invalidRequiredValues() {
		Stream<Arguments> strings = Stream.of("name", "address", "roadAddress", "sggCode", "umdName", "propertyType")
			.flatMap(field -> Stream.of("missing", "null", "empty", "blank").map(kind -> Arguments.of(field, kind)));
		Stream<Arguments> otherRequired = Stream.of("deposit", "monthlyRent", "leaseType")
			.flatMap(field -> Stream.of("missing", "null").map(kind -> Arguments.of(field, kind)));
		return Stream.concat(strings, otherRequired);
	}

	@ParameterizedTest
	@MethodSource("invalidValues")
	void invalidValuesAreRejectedWithoutCallingProvider(String field, Object value) throws Exception {
		Map<String, Object> body = minimalBody();
		body.put(field, value);
		assertInvalid(body);
	}

	static Stream<Arguments> invalidValues() {
		return Stream.of(
			Arguments.of("name", "가".repeat(101)), Arguments.of("address", "가".repeat(256)),
			Arguments.of("roadAddress", "가".repeat(256)), Arguments.of("sggCode", "1".repeat(21)),
			Arguments.of("sggCode", "4111"), Arguments.of("sggCode", "411150"),
			Arguments.of("sggCode", "41A15"), Arguments.of("sggCode", " 41115"),
			Arguments.of("sggCode", "41115 "), Arguments.of("sggCode", "４１１１５"),
			Arguments.of("sggCode", "41115\n"),
			Arguments.of("umdName", "가".repeat(51)), Arguments.of("propertyType", "가".repeat(21)),
			Arguments.of("direction", "가".repeat(11)), Arguments.of("direction", "  "),
			Arguments.of("description", "가".repeat(16384)),
			Arguments.of("deposit", -1), Arguments.of("monthlyRent", -1),
			Arguments.of("deposit", 2147483648L), Arguments.of("monthlyRent", "not-a-number"),
			Arguments.of("deposit", new BigDecimal("1000.5")), Arguments.of("monthlyRent", new BigDecimal("50.5")),
			Arguments.of("deposit", "1000"), Arguments.of("monthlyRent", true),
			Arguments.of("floor", new BigDecimal("3.5")), Arguments.of("totalFloors", new BigDecimal("10.5")),
			Arguments.of("buildYear", new BigDecimal("2020.5")),
			Arguments.of("exclusiveArea", 0), Arguments.of("exclusiveArea", -1),
			Arguments.of("exclusiveArea", new BigDecimal("1000000.00")),
			Arguments.of("exclusiveArea", new BigDecimal("23.501")),
			Arguments.of("totalFloors", 0), Arguments.of("totalFloors", -1),
			Arguments.of("buildYear", 0), Arguments.of("buildYear", -1),
			Arguments.of("leaseType", "SALE"), Arguments.of("leaseType", "monthly"),
			Arguments.of("leaseType", ""), Arguments.of("leaseType", "  "),
			Arguments.of("leaseType", 0), Arguments.of("leaseType", 1),
			Arguments.of("leaseType", true), Arguments.of("monthlyRent", 0),
			Arguments.of("leaseType", "JEONSE")
		);
	}

	@Test
	void malformedJsonIsRejected() throws Exception {
		mvc.perform(post("/api/properties").header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertNoWork();
	}

	@Test
	void allOptionalFieldsMayExplicitlyBeNull() throws Exception {
		Map<String, Object> body = minimalBody();
		Stream.of("exclusiveArea", "floor", "totalFloors", "buildYear", "direction", "description")
			.forEach(field -> body.put(field, null));
		create(bearer(admin), body).andExpect(status().isCreated());
	}

	@Test
	void missingCoordinatesReturnBadRequestWithoutSaving() throws Exception {
		geocoding.result = Optional.empty();
		create(bearer(admin), minimalBody()).andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_GEOCODABLE"));
		assertThat(properties.count()).isZero();
	}

	@Test
	void providerFailureReturnsBadGatewayWithoutSaving() throws Exception {
		geocoding.failure = new GeocodingUnavailableException("provider unavailable");
		create(bearer(admin), minimalBody()).andExpect(status().isBadGateway())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"));
		assertThat(properties.count()).isZero();
	}

	@Test
	void jeonsePropertyIsReturnedWithLeaseTypeInFavoriteListAndDetail() throws Exception {
		Map<String, Object> body = minimalBody();
		body.put("leaseType", "JEONSE");
		body.put("deposit", 20000);
		body.put("monthlyRent", 0);
		var response = create(bearer(admin), body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.leaseType").value("JEONSE"))
			.andExpect(jsonPath("$.deposit").value(20000))
			.andExpect(jsonPath("$.monthlyRent").value(0)).andReturn();
		String id = json.readTree(response.getResponse().getContentAsString()).path("id").asString();
		String userToken = bearer(ordinary);
		mvc.perform(post("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, userToken)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("propertyId", id))))
			.andExpect(status().isCreated());
		mvc.perform(get("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].property.leaseType").value("JEONSE"));
		mvc.perform(get("/api/me/favorites/" + id).header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.property.leaseType").value("JEONSE"))
			.andExpect(jsonPath("$.property.monthlyRent").value(0));
	}

	@Test
	void newlyRegisteredPropertyCanBeFavoritedAndRead() throws Exception {
		var response = create(bearer(admin), minimalBody()).andExpect(status().isCreated()).andReturn();
		String id = json.readTree(response.getResponse().getContentAsString()).path("id").asString();
		String userToken = bearer(ordinary);
		mvc.perform(post("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, userToken)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("propertyId", id))))
			.andExpect(status().isCreated());
		mvc.perform(get("/api/me/favorites/" + id).header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.property.id").value(id))
			.andExpect(jsonPath("$.property.name").value("테스트 매물"))
			.andExpect(jsonPath("$.property.leaseType").value("MONTHLY"))
			.andExpect(jsonPath("$.property.deposit").value(1000))
			.andExpect(jsonPath("$.property.latitude").value(LOCATION.lat()))
			.andExpect(jsonPath("$.property.longitude").value(LOCATION.lng()));
	}

	private void assertInvalid(Map<String, Object> body) throws Exception {
		create(bearer(admin), body).andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertNoWork();
	}

	@Test
	void seoulRegistrationAutomaticallyStoresFourSafetyMetrics() throws Exception {
		var response = create(bearer(admin), seoulBody()).andExpect(status().isCreated()).andReturn();
		String id = json.readTree(response.getResponse().getContentAsString()).path("id").asString();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE property_id = UUID_TO_BIN(?)", Long.class, id))
			.isEqualTo(4);
		for (Map.Entry<String, Integer> expected : Map.of("CCTV_COUNT_500M", 4,
			"EMERGENCY_BELL_COUNT_500M", 2, "SECURITY_LIGHT_COUNT_500M", 4).entrySet()) {
			assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE property_id = UUID_TO_BIN(?) AND metric_code = ?",
				BigDecimal.class, id, expected.getKey())).isEqualByComparingTo(BigDecimal.valueOf(expected.getValue()));
		}
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE property_id = UUID_TO_BIN(?) AND metric_code = ?",
			BigDecimal.class, id, "NEAREST_POLICE_STATION_DISTANCE").doubleValue()).isBetween(110.0, 112.0);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category = 'SAFETY' AND computed_at IS NOT NULL", Long.class))
			.isEqualTo(4);
	}

	@Test
	void sourceFailurePreservesPropertyAndSuccessfulIndependentMetrics() throws Exception {
		safetySource.failure = Kind.CCTV;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE metric_code = 'CCTV_COUNT_500M'", Long.class)).isZero();
	}

	@Test
	void recollectionUpdatesWithoutDuplicatesAndFailedMetricKeepsItsPreviousValue() throws Exception {
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		safetySource.failure = Kind.CCTV;
		safetyMetrics.collect(properties.findAll().getFirst());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code = 'CCTV_COUNT_500M'", BigDecimal.class))
			.isEqualByComparingTo("4");
	}

	@Test
	void nonSeoulRegistrationDoesNotLoadSafetyData() throws Exception {
		create(bearer(admin), minimalBody()).andExpect(status().isCreated());
		assertThat(safetySource.calls).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isZero();
	}

	@Test
	void successfulEmptyDataStoresZeroCountsButNotZeroStationDistance() throws Exception {
		safetySource.empty = true;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE numeric_value = 0 AND unit = 'count'", Long.class))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE unit = 'm'", Long.class)).isZero();
	}

	@Test
	void allSourcesFailWithoutRollingBackRegisteredPropertyOrWritingFakeZeros() throws Exception {
		safetySource.unavailable = true;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isZero();
	}

	@Test
	void successfulRecollectionActuallyUpdatesStoredQuantitiesWithoutReplacingRows() throws Exception {
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		String id = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM property_feature WHERE metric_code = 'CCTV_COUNT_500M'", String.class);
		safetySource.multiplier = 2;
		safetyMetrics.collect(properties.findAll().getFirst());
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE id = UUID_TO_BIN(?)", BigDecimal.class, id))
			.isEqualByComparingTo("8");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isEqualTo(4);
	}

	@Test
	void registrationReadsCsvAdapterAndPersistsRealCalculatedMetrics(@TempDir Path directory) throws Exception {
		// Synthetic fixtures exercise CSV -> registration -> MySQL, not a live provider.
		geocoding.result = Optional.of(new Coordinates(37.5663, 126.978));
		Files.writeString(directory.resolve("seoul.geojson"), """
			{"type":"FeatureCollection","features":[{"type":"Feature","geometry":
			{"type":"Polygon","coordinates":[[[126,37],[128,37],[128,38],[126,38],[126,37]]]}}]}
			""");
		var encoding = Charset.forName("MS949");
		Files.writeString(directory.resolve("cctv.csv"), """
			관리번호,WGS84위도,WGS84경도,카메라대수
			1,37.5663,126.978,2
			2,37.5663,126.978,3
			3,37.5663,216.994723,9
			""", encoding);
		Files.writeString(directory.resolve("emergency-bell.csv"), """
			관리번호,WGS84위도,WGS84경도
			1,37.5663,126.978
			2,37.5663,126.978
			""", encoding);
		Files.writeString(directory.resolve("police.csv"), "시도청,주소\n서울청,서울 관서\n부산청,부산 관서\n", encoding);
		safetySource.delegate = new CsvSafetyFacilitySource(directory, address -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(address).isEqualTo("서울 관서");
			return Optional.of(new Coordinates(37.5673, 126.978));
		});
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature", Long.class)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code = 'CCTV_COUNT_500M'", BigDecimal.class))
			.isEqualByComparingTo("5");
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code = 'EMERGENCY_BELL_COUNT_500M'", BigDecimal.class))
			.isEqualByComparingTo("2");
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code = 'NEAREST_POLICE_STATION_DISTANCE'", BigDecimal.class))
			.isBetween(new BigDecimal("110"), new BigDecimal("112"));
	}

	private static Map<String, Object> seoulBody() {
		var body = minimalBody();
		body.put("address", "서울특별시 강남구 역삼동 1");
		body.put("roadAddress", "서울특별시 강남구 테헤란로 1");
		body.put("sggCode", "11680");
		body.put("umdName", "역삼동");
		return body;
	}

	private void assertNoWork() {
		assertThat(properties.count()).isZero();
		assertThat(geocoding.calls).isZero();
		assertThat(safetySource.calls).isZero();
	}

	private ResultActions create(String token, Map<String, Object> body) throws Exception {
		return mvc.perform(post("/api/properties").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken();
	}

	private User saveUser(String name, UserRole role) {
		return users.save(User.builder().provider("google").providerId(name).email(name + "@example.com")
			.nickname(name).role(role).build());
	}

	private void changeRole(User user, UserRole role) {
		jdbc.update("UPDATE users SET role = ? WHERE id = UUID_TO_BIN(?)", role.name(), user.getId().toString());
	}

	private static Map<String, Object> minimalBody() {
		return new LinkedHashMap<>(Map.of(
			"name", "테스트 매물", "address", "경기 수원시 팔달구 우만동 228",
			"roadAddress", "경기 수원시 팔달구 월드컵로 205", "sggCode", "41115", "umdName", "우만동",
			"propertyType", "원룸", "leaseType", "MONTHLY", "deposit", 1000, "monthlyRent", 50
		));
	}

	static class StubGeocodingClient implements GeocodingClient {
		int calls;
		String address;
		Optional<Coordinates> result;
		RuntimeException failure;

		@Override
		public Optional<Coordinates> locate(String roadAddress) {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			calls++;
			address = roadAddress;
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	static class StubSafetySource implements SafetyFacilitySource {
		SafetyFacilitySource delegate;
		Kind failure;
		int calls;
		boolean empty;
		boolean unavailable;
		int multiplier = 1;
		@Override
		public List<Facility> load(Kind kind) {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			calls++;
			if (delegate != null) return delegate.load(kind);
			if (unavailable || kind == failure) throw new IllegalStateException("Test source failure");
			if (empty) return List.of();
			var here = new Facility(LOCATION.lat(), LOCATION.lng(), multiplier);
			var near = new Facility(LOCATION.lat() + 0.001, LOCATION.lng(), 3 * multiplier);
			var far = new Facility(LOCATION.lat() + 0.01, LOCATION.lng(), 7);
			return switch (kind) {
				case CCTV, SECURITY_LIGHT -> List.of(here, near, far);
				case EMERGENCY_BELL -> List.of(here, new Facility(near.latitude(), near.longitude(), 1), far);
				case POLICE_STATION -> List.of(far, near);
			};
		}
	}

	@Test
	void noiseRegistrationStoresSixMetricsWithoutHoldingTransactionDuringSourceCall() throws Exception {
		noiseSource.enabled = true;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='NOISE'", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code='SENSOR_AVG_NOISE_7D'", BigDecimal.class)).isEqualByComparingTo("50");
		assertThat(jdbc.queryForObject("SELECT text_value FROM property_feature WHERE metric_code='SENSOR_ID'", String.class)).isEqualTo("test-sensor");
	}

	@Test
	void insufficientNoiseCoverageDoesNotPreventPropertyOrSafetyStorage() throws Exception {
		noiseSource.enabled = true;
		noiseSource.hours = 83;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='NOISE'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='SAFETY'", Integer.class)).isEqualTo(4);
	}

	@Test
	void noiseSourceFailureDoesNotPreventRegistration() throws Exception {
		noiseSource.enabled = true;
		noiseSource.fail = true;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='NOISE'", Integer.class)).isZero();
	}

	@Test
	void noiseRecollectionUpdatesWithoutDuplicatesAndPreservesSuccessfulSnapshotOnFailure() throws Exception {
		noiseSource.enabled = true;
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		Property property = properties.findAll().getFirst();
		noiseSource.value = "60";
		noiseMetrics.collect(property);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='NOISE'", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code='SENSOR_AVG_NOISE_7D'", BigDecimal.class)).isEqualByComparingTo("60");
		noiseSource.fail = true;
		noiseMetrics.collect(property);
		assertThat(jdbc.queryForObject("SELECT numeric_value FROM property_feature WHERE metric_code='SENSOR_AVG_NOISE_7D'", BigDecimal.class)).isEqualByComparingTo("60");
	}

	@Test
	void partialNoiseDatabaseFailureRollsBackWholeSnapshotWithoutRollingBackPropertyOrSafety() throws Exception {
		noiseSource.enabled = true;
		// The third write exceeds VARCHAR(255), after two numeric writes have succeeded.
		noiseSource.sensorId = "x".repeat(300);
		create(bearer(admin), seoulBody()).andExpect(status().isCreated());
		assertThat(properties.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='NOISE'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property_feature WHERE category='SAFETY'", Integer.class)).isEqualTo(4);
	}

	static class StubNoiseSource implements NoiseObservationSource {
		boolean enabled;
		boolean fail;
		int hours = 84;
		String value = "50";
		String sensorId = "test-sensor";
		public List<Sensor> sensors() {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			if (fail) throw new IllegalStateException("Unavailable");
			return enabled ? List.of(new Sensor(sensorId, LOCATION.lat(), LOCATION.lng())) : List.of();
		}
		public List<Observation> observations(Sensor sensor, LocalDateTime start, LocalDateTime end) {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			assertThat(end).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).atStartOfDay());
			assertThat(start).isEqualTo(end.minusDays(7));
			return Stream.iterate(start, time -> time.plusHours(1)).limit(hours)
				.map(time -> new Observation(sensor.id(), time, value)).toList();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubGeocodingConfiguration {
		@Bean
		@Primary
		StubNoiseSource noiseSource() { return new StubNoiseSource(); }
		@Bean
		@Primary
		StubSafetySource stubSafetySource() { return new StubSafetySource(); }
		@Bean
		@Primary
		StubGeocodingClient stubGeocodingClient() {
			return new StubGeocodingClient();
		}
	}
}
