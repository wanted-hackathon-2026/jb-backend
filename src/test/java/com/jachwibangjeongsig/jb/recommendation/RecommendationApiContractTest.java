package com.jachwibangjeongsig.jb.recommendation;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingUnavailableException;
import com.jachwibangjeongsig.jb.global.llm.LlmClient;
import com.jachwibangjeongsig.jb.global.llm.LlmUnavailableException;
import com.jachwibangjeongsig.jb.property.entity.LeaseType;
import com.jachwibangjeongsig.jb.property.entity.Property;
import com.jachwibangjeongsig.jb.property.repository.PropertyRepository;
import com.jachwibangjeongsig.jb.recommendation.service.RecommendationProcessor.Evaluation;
import com.jachwibangjeongsig.jb.recommendation.service.RecommendationProcessor.Evaluations;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;
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
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key",
	"kakao.local.api-key=",
	"infrastructure.bus-stop-file=build/test-data/missing-bus-stops.csv"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(RecommendationApiContractTest.StubConfiguration.class)
class RecommendationApiContractTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	private static final double WORKPLACE_LAT = 37.5;
	private static final double WORKPLACE_LNG = 127.0;
	/** 위도 1도는 약 111km. 1km 남짓 떨어뜨리는 값이다. */
	private static final double ONE_KILOMETRE = 1 / 111.0;
	private static final String CLIENT_SESSION = "X-Client-Session";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;
	@Autowired JwtTokenService tokens;
	@Autowired WorkplaceRepository workplaces;
	@Autowired PropertyRepository properties;
	@Autowired StubLlmClient llm;
	@Autowired StubGeocodingClient geocoding;

	private User owner;
	private User stranger;
	private Workplace workplace;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM recommendation_result");
		jdbc.update("DELETE FROM recommendation_criteria");
		jdbc.update("DELETE FROM recommendation");
		jdbc.update("DELETE FROM property_feature");
		jdbc.update("DELETE FROM favorite");
		jdbc.update("DELETE FROM property");
		jdbc.update("DELETE FROM workplace");
		jdbc.update("DELETE FROM users");
		jdbc.update("DELETE FROM client_session");
		llm.reset();
		geocoding.result = Optional.of(new Coordinates(WORKPLACE_LAT, WORKPLACE_LNG));
		geocoding.failure = null;
		owner = saveUser("owner");
		stranger = saveUser("stranger");
		workplace = workplaces.save(Workplace.builder().user(owner).name("본사")
			.roadAddress("서울 강남구 강남대로 1").lat(WORKPLACE_LAT).lng(WORKPLACE_LNG).build());
	}

	@Test
	void requestingIsAcceptedImmediatelyAndReportsCompletionAfterwards() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		saveProperty("조금 먼 원룸", 3, 1000, 50);

		String recommendationId = request(bearer(owner), body())
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.recommendationId").isNotEmpty())
			.andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"recommendationId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/recommendations/" + recommendationId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.requestedAt").isNotEmpty())
			.andExpect(jsonPath("$.startedAt").isNotEmpty())
			.andExpect(jsonPath("$.completedAt").isNotEmpty())
			.andExpect(jsonPath("$.failureReason").doesNotExist());

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].evaluation.rank").value(1))
			.andExpect(jsonPath("$.content[1].evaluation.rank").value(2))
			.andExpect(jsonPath("$.content[0].evaluation.totalScore")
				.value(org.hamcrest.Matchers.greaterThanOrEqualTo(
					0)))
			.andExpect(jsonPath("$.content[0].evaluation.summary").isNotEmpty())
			.andExpect(jsonPath("$.content[0].evaluation.commuteMinutes").isNumber());
	}

	@Test
	void propertiesOutsideTheCommuteRadiusNeverReachTheModel() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		saveProperty("아주 먼 원룸", 60, 1000, 50);

		request(bearer(owner), body()).andExpect(status().isAccepted());

		assertThat(llm.lastUserPrompt).contains("가까운 원룸").doesNotContain("아주 먼 원룸");
	}

	@Test
	void propertiesOutsideTheBudgetNeverReachTheModel() throws Exception {
		saveProperty("예산 안 원룸", 1, 1000, 50);
		saveProperty("너무 비싼 원룸", 1, 9000, 50);

		request(bearer(owner), body()).andExpect(status().isAccepted());

		assertThat(llm.lastUserPrompt).contains("예산 안 원룸").doesNotContain("너무 비싼 원룸");
	}

	@Test
	void collectedMetricsAreHandedToTheModel() throws Exception {
		UUID propertyId = saveProperty("지표 있는 원룸", 1, 1000, 50);
		jdbc.update("""
			INSERT INTO property_feature (id, property_id, category, metric_code, text_value, unit, computed_at)
			VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'SUNLIGHT', 'SUNLIGHT_ESTIMATE_LEVEL', 'GOOD', 'level', ?)
			""", UUID.randomUUID().toString(), propertyId.toString(), LocalDateTime.now());

		request(bearer(owner), body()).andExpect(status().isAccepted());

		assertThat(llm.lastUserPrompt).contains("SUNLIGHT/SUNLIGHT_ESTIMATE_LEVEL: GOOD level");
	}

	@Test
	void anEmptyCandidateSetCompletesWithoutCallingTheModel() throws Exception {
		saveProperty("예산 밖 원룸", 1, 9000, 50);

		String recommendationId = requestAndExtractId();

		assertThat(llm.calls).isZero();
		mvc.perform(get("/api/recommendations/" + recommendationId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(jsonPath("$.status").value("COMPLETED"));
		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(0));
	}

	@Test
	void aModelOutageIsRecordedAsFailedAndResultsAreRefused() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		llm.failure = new LlmUnavailableException("OpenRouter request failed");

		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("OpenRouter")));

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_READY"));
	}

	@Test
	void evaluationsThatPointNowhereOrSayNothingAreDiscarded() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		saveProperty("조금 먼 원룸", 3, 1000, 50);
		llm.evaluations = List.of(
			new Evaluation(1, 80, 80, 80, 80, 80, 90, "쓸 만한 총평"),
			new Evaluation(99, 10, 10, 10, 10, 10, 95, "존재하지 않는 후보"),
			new Evaluation(2, 10, 10, 10, 10, 10, 70, "   "),
			new Evaluation(1, 50, 50, 50, 50, 50, 60, "같은 후보 중복"));

		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].name").value("가까운 원룸"))
			.andExpect(jsonPath("$.content[0].evaluation.summary").value("쓸 만한 총평"));
	}

	@Test
	void scoresOutsideTheAllowedRangeAreClampedBeforeTheyAreStored() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		llm.evaluations = List.of(new Evaluation(1, -20, 500, 50, 50, 50, 300, "범위를 벗어난 점수"));

		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(jsonPath("$.content[0].evaluation.sunlightScore").value(0))
			.andExpect(jsonPath("$.content[0].evaluation.quietnessScore").value(100))
			.andExpect(jsonPath("$.content[0].evaluation.totalScore").value(100));
	}

	@Test
	void theDetailEndpointReturnsTheListingAndItsEvaluation() throws Exception {
		UUID propertyId = saveProperty("가까운 원룸", 1, 1000, 50);

		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties/" + propertyId)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.property.id").value(propertyId.toString()))
			.andExpect(jsonPath("$.property.name").value("가까운 원룸"))
			.andExpect(jsonPath("$.evaluation.rank").value(1))
			.andExpect(jsonPath("$.evaluation.summary").isNotEmpty());
	}

	@Test
	void aPropertyThatWasNotPartOfThisRecommendationIsNotFound() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		UUID other = saveProperty("추천에 없는 원룸", 60, 1000, 50);

		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties/" + other)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
	}

	@Test
	void anotherUsersRecommendationIsHiddenRatherThanForbidden() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String recommendationId = requestAndExtractId();

		for (String path : List.of("", "/properties")) {
			mvc.perform(get("/api/recommendations/" + recommendationId + path)
					.header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
		}
	}

	@Test
	void anotherUsersWorkplaceCannotSeedARecommendation() throws Exception {
		request(bearer(stranger), body())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKPLACE_NOT_FOUND"));
	}

	@Test
	void invertedBudgetRangesAreRejected() throws Exception {
		Map<String, Object> body = body();
		body.put("depositMin", 5000);
		body.put("depositMax", 1000);

		request(bearer(owner), body).andExpect(status().isBadRequest());
	}

	@Test
	void anUnknownTransportTypeIsRejected() throws Exception {
		Map<String, Object> body = body();
		body.put("transportType", "TELEPORT");

		request(bearer(owner), body).andExpect(status().isBadRequest());
	}

	@Test
	void servesAGuestIdentifiedOnlyByTheClientSessionHeader() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String token = UUID.randomUUID().toString();

		String recommendationId = guestRequest(token, guestBody())
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"recommendationId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/recommendations/" + recommendationId).header(CLIENT_SESSION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(CLIENT_SESSION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].evaluation.rank").value(1));
	}

	@Test
	void storesOnlyTheHashOfTheGuestToken() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String token = UUID.randomUUID().toString();

		guestRequest(token, guestBody()).andExpect(status().isAccepted());

		List<String> stored = jdbc.queryForList(
			"SELECT session_hash_token FROM client_session", String.class);
		assertThat(stored).hasSize(1);
		assertThat(stored.getFirst()).isNotEqualTo(token).hasSize(64);
	}

	@Test
	void reusesTheSameSessionRowForRepeatedRequests() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String token = UUID.randomUUID().toString();

		guestRequest(token, guestBody()).andExpect(status().isAccepted());
		guestRequest(token, guestBody()).andExpect(status().isAccepted());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM client_session", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation", Integer.class)).isEqualTo(2);
	}

	@Test
	void keepsOneGuestFromReadingAnothers() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String mine = UUID.randomUUID().toString();
		String recommendationId = guestRequest(mine, guestBody())
			.andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"recommendationId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

		mvc.perform(get("/api/recommendations/" + recommendationId)
				.header(CLIENT_SESSION, UUID.randomUUID().toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));

		mvc.perform(get("/api/recommendations/" + recommendationId)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isNotFound());

		mvc.perform(get("/api/recommendations/" + recommendationId).header(CLIENT_SESSION, mine))
			.andExpect(status().isOk());
	}

	@Test
	void keepsAGuestFromReadingALoggedInUsersRecommendation() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);
		String recommendationId = requestAndExtractId();

		mvc.perform(get("/api/recommendations/" + recommendationId)
				.header(CLIENT_SESSION, UUID.randomUUID().toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
	}

	@Test
	void rejectsAGuestRequestWithoutTheClientSessionHeader() throws Exception {
		mvc.perform(post("/api/recommendations").contentType(APPLICATION_JSON)
				.content(json.writeValueAsString(guestBody())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CLIENT_SESSION_REQUIRED"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation", Integer.class)).isZero();
	}

	@Test
	void refusesAGuestPointingAtASavedWorkplace() throws Exception {
		// 비로그인 사용자는 거점을 가질 수 없다. 남의 거점처럼 존재를 감춘다.
		guestRequest(UUID.randomUUID().toString(), body())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKPLACE_NOT_FOUND"));
	}

	@Test
	void requiresExactlyOneWorkplaceForm() throws Exception {
		Map<String, Object> both = guestBody();
		both.put("workplaceId", workplace.getId().toString());
		guestRequest(UUID.randomUUID().toString(), both)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		Map<String, Object> neither = guestBody();
		neither.remove("workplace");
		guestRequest(UUID.randomUUID().toString(), neither)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void letsALoggedInUserSupplyAnInlineWorkplaceToo() throws Exception {
		saveProperty("가까운 원룸", 1, 1000, 50);

		request(bearer(owner), guestBody()).andExpect(status().isAccepted());

		assertThat(jdbc.queryForObject(
			"SELECT workplace_name FROM recommendation_criteria", String.class)).isEqualTo("이번만 쓰는 회사");
	}

	@Test
	void reportsBadRequestWhenTheInlineWorkplaceCannotBeGeocoded() throws Exception {
		geocoding.result = Optional.empty();

		guestRequest(UUID.randomUUID().toString(), guestBody())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_GEOCODABLE"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation", Integer.class)).isZero();
	}

	@Test
	void reportsBadGatewayWhenGeocodingIsDown() throws Exception {
		geocoding.failure = new GeocodingUnavailableException("boom");

		guestRequest(UUID.randomUUID().toString(), guestBody())
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"));
	}

	private ResultActions guestRequest(String token, Map<String, Object> body) throws Exception {
		return mvc.perform(post("/api/recommendations").header(CLIENT_SESSION, token)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	/** 저장된 거점 대신 이번 요청에만 쓸 주소를 넣은 본문. */
	private Map<String, Object> guestBody() {
		Map<String, Object> body = body();
		body.remove("workplaceId");
		body.put("workplace", Map.of("name", "이번만 쓰는 회사", "roadAddress", "서울 강남구 강남대로 1"));
		return body;
	}

	private String requestAndExtractId() throws Exception {
		return request(bearer(owner), body()).andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString()
			.replaceAll(".*\"recommendationId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}

	private ResultActions request(String token, Map<String, Object> body) throws Exception {
		return mvc.perform(post("/api/recommendations").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private Map<String, Object> body() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("workplaceId", workplace.getId().toString());
		body.put("transportType", "TRANSIT");
		body.put("maxCommuteMinutes", 30);
		body.put("sunlightImportance", 5);
		body.put("quietnessImportance", 4);
		body.put("safetyImportance", 3);
		body.put("infrastructureImportance", 2);
		body.put("depositMin", 0);
		body.put("depositMax", 2000);
		body.put("monthlyRentMin", 0);
		body.put("monthlyRentMax", 80);
		body.put("roomTypes", List.of("원룸"));
		return body;
	}

	private UUID saveProperty(String name, double kilometresAway, int deposit, int monthlyRent) {
		return properties.save(Property.builder()
			.name(name).address("서울 강남구 역삼동 1").roadAddress("서울 강남구 테헤란로 1")
			.sggCode("11680").umdName("역삼동")
			.lat(WORKPLACE_LAT + kilometresAway * ONE_KILOMETRE).lng(WORKPLACE_LNG)
			.propertyType("원룸").leaseType(LeaseType.MONTHLY).deposit(deposit).monthlyRent(monthlyRent)
			.build()).getId();
	}

	private String bearer(User user) {
		return "Bearer " + tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken();
	}

	private User saveUser(String name) {
		return users.save(User.builder().provider("google").providerId(name).email(name + "@example.com")
			.nickname(name).role(UserRole.USER).build());
	}

	/** 프롬프트에 적힌 후보 번호를 그대로 돌려주는 모델 대역. */
	static class StubLlmClient implements LlmClient {

		private static final Pattern CANDIDATE = Pattern.compile("(?m)^후보 (\\d+):");

		int calls;
		String lastUserPrompt;
		RuntimeException failure;
		List<Evaluation> evaluations;

		void reset() {
			calls = 0;
			lastUserPrompt = null;
			failure = null;
			evaluations = null;
		}

		@Override
		public <T> T complete(String systemPrompt, String userPrompt, Class<T> responseType) {
			calls++;
			lastUserPrompt = userPrompt;
			if (failure != null) {
				throw failure;
			}
			return responseType.cast(new Evaluations(
				evaluations != null ? evaluations : defaultEvaluations(userPrompt)));
		}

		private static List<Evaluation> defaultEvaluations(String userPrompt) {
			List<Evaluation> generated = new ArrayList<>();
			Matcher matcher = CANDIDATE.matcher(userPrompt);
			while (matcher.find()) {
				int number = Integer.parseInt(matcher.group(1));
				// 후보 순서가 그대로 순위가 되도록 총점을 매긴다.
				generated.add(new Evaluation(number, 70, 70, 70, 70, 70, 100 - number, "후보 " + number + " 총평"));
			}
			return generated;
		}
	}

	/** 인라인 거점은 서버가 지오코딩한다. 네트워크를 타지 않게 고정한다. */
	static class StubGeocodingClient implements GeocodingClient {

		Optional<Coordinates> result = Optional.empty();
		RuntimeException failure;

		@Override
		public Optional<Coordinates> locate(String roadAddress) {
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubConfiguration {

		@Bean
		@Primary
		StubGeocodingClient stubGeocodingClient() {
			return new StubGeocodingClient();
		}

		@Bean
		@Primary
		StubLlmClient stubLlmClient() {
			return new StubLlmClient();
		}

		/** @Async 를 인라인 실행시켜 비동기 타이밍 없이 계약을 검증한다. */
		@Bean
		TaskExecutor taskExecutor() {
			return new SyncTaskExecutor();
		}
	}
}
