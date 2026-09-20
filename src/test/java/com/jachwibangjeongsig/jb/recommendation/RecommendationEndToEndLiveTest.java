package com.jachwibangjeongsig.jb.recommendation;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진짜 외부 API 를 친다. 대역이 하나도 없다. 키를 export 했을 때만 실행된다:
 *   OPENROUTER_API_KEY=... ./gradlew test --tests '*RecommendationEndToEndLiveTest'
 * (vworld.api-key 등 나머지는 .env 에서 읽힌다.)
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RecommendationEndToEndLiveTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;
	@Autowired JwtTokenService tokens;
	@Autowired WorkplaceRepository workplaces;

	@Test
	void registeringAListingCollectsMetricsAndTheRecommendationRunsThroughTheRealModel() throws Exception {
		User admin = users.save(User.builder().provider("google").providerId("e2e-admin")
			.email("e2e@example.com").nickname("e2e").role(UserRole.ADMIN).build());
		String token = "Bearer " + tokens.issue(admin, UUID.randomUUID(), Instant.now()).accessToken();

		// 1. 매물 등록 — 주소를 좌표로 바꾸는 VWorld 호출이 실제로 나간다.
		Map<String, Object> listing = new LinkedHashMap<>(Map.of(
			"name", "E2E 역삼 원룸", "address", "서울 강남구 역삼동 737",
			"roadAddress", "서울 강남구 테헤란로 152", "sggCode", "11680", "umdName", "역삼동",
			"propertyType", "원룸", "leaseType", "MONTHLY", "deposit", 1000, "monthlyRent", 60));
		listing.put("direction", "남향");
		listing.put("floor", 5);
		listing.put("totalFloors", 12);
		listing.put("buildYear", 2019);
		listing.put("exclusiveArea", 23.5);

		JsonNode created = json.readTree(mvc.perform(post("/api/properties")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(APPLICATION_JSON).content(json.writeValueAsString(listing)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString());
		UUID propertyId = UUID.fromString(created.path("id").asString());

		Map<String, Object> coordinates = jdbc.queryForMap(
			"SELECT lat, lng FROM property WHERE id = UUID_TO_BIN(?)", propertyId.toString());
		System.out.println("\n### 1. 매물 등록 (VWorld 실호출) ###");
		System.out.println("  id=" + propertyId + " lat=" + coordinates.get("lat") + " lng=" + coordinates.get("lng"));
		double lat = ((Number) coordinates.get("lat")).doubleValue();
		double lng = ((Number) coordinates.get("lng")).doubleValue();
		assertThat(lat).isBetween(33.0, 39.0);
		assertThat(lng).isBetween(124.0, 132.0);

		// 2. 등록과 함께 자동 수집된 지표.
		List<Map<String, Object>> features = jdbc.queryForList("""
			SELECT category, metric_code, numeric_value, text_value, unit
			FROM property_feature WHERE property_id = UUID_TO_BIN(?) ORDER BY category, metric_code
			""", propertyId.toString());
		System.out.println("\n### 2. 자동 수집된 property_feature (" + features.size() + "건) ###");
		features.forEach(row -> System.out.println("  " + row));

		// 3. 추천 요청 — 근무지는 매물에서 2km 남짓 떨어뜨린다.
		Workplace workplace = workplaces.save(Workplace.builder().user(admin).name("E2E 근무지")
			.roadAddress("서울 강남구 강남대로 396").lat(lat + 0.018).lng(lng).build());
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("workplaceId", workplace.getId().toString());
		request.put("transportType", "TRANSIT");
		request.put("maxCommuteMinutes", 30);
		request.put("sunlightImportance", 5);
		request.put("quietnessImportance", 4);
		request.put("safetyImportance", 3);
		request.put("infrastructureImportance", 2);
		request.put("depositMin", 0);
		request.put("depositMax", 3000);
		request.put("monthlyRentMin", 0);
		request.put("monthlyRentMax", 100);
		request.put("roomTypes", List.of("원룸"));

		JsonNode accepted = json.readTree(mvc.perform(post("/api/recommendations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(APPLICATION_JSON).content(json.writeValueAsString(request)))
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString());
		String recommendationId = accepted.path("recommendationId").asString();
		System.out.println("\n### 3. 추천 접수 ###");
		System.out.println("  " + accepted);
		assertThat(accepted.path("status").asString()).isEqualTo("PENDING");

		// 4. 비동기 처리를 실제로 기다린다. OpenRouter 왕복이 들어 있다.
		JsonNode statusNode = null;
		for (int attempt = 0; attempt < 60; attempt++) {
			Thread.sleep(2000);
			statusNode = json.readTree(mvc.perform(get("/api/recommendations/" + recommendationId)
					.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
			String state = statusNode.path("status").asString();
			if ("COMPLETED".equals(state) || "FAILED".equals(state)) {
				break;
			}
		}
		System.out.println("\n### 4. 처리 상태 ###");
		System.out.println("  " + statusNode);
		assertThat(statusNode.path("status").asString()).isEqualTo("COMPLETED");

		// 5. 실제 모델이 매긴 평가.
		JsonNode results = json.readTree(mvc.perform(get("/api/recommendations/" + recommendationId + "/properties")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		System.out.println("\n### 5. 추천 결과 ###");
		System.out.println(results.toPrettyString());
		assertThat(results.path("content").size()).isEqualTo(1);
		JsonNode evaluation = results.path("content").path(0).path("evaluation");
		assertThat(evaluation.path("summary").asString()).isNotBlank();
		assertThat(evaluation.path("totalScore").asInt()).isBetween(0, 100);
	}
}
