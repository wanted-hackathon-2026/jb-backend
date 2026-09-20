package com.jachwibangjeongsig.jb.workplace;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import com.jachwibangjeongsig.jb.user.UserRole;
import com.jachwibangjeongsig.jb.global.geocoding.Coordinates;
import com.jachwibangjeongsig.jb.workplace.entity.Workplace;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingUnavailableException;
import com.jachwibangjeongsig.jb.workplace.repository.WorkplaceRepository;
import com.jachwibangjeongsig.jb.global.geocoding.GeocodingClient;

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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"auth.google-client-id=test-google-client-id",
	"auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
	"auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
	"vworld.api-key=test-vworld-api-key",
	"auth.cookie-secure=true",
	"auth.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(WorkplaceApiContractTest.StubGeocodingConfiguration.class)
class WorkplaceApiContractTest {

	private static final Coordinates SUWON = new Coordinates(37.279609852101984, 127.04339808904444);
	private static final Coordinates SEOUL = new Coordinates(37.56661, 126.97839);
	private static final String BODY = """
		{"name":"회사","roadAddress":"경기 수원시 팔달구 월드컵로 205"}
		""";

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	WorkplaceRepository workplaceRepository;

	@Autowired
	JwtTokenService jwtTokenService;

	@Autowired
	StubGeocodingClient geocoding;

	private User owner;

	@BeforeEach
	void reset() {
		workplaceRepository.deleteAll();
		userRepository.deleteAll();
		geocoding.result = Optional.of(SUWON);
		geocoding.failure = null;
		geocoding.calls = 0;
		owner = saveUser("owner@example.com", "google-sub-owner");
	}

	@Test
	void createsWorkplaceWithCoordinatesResolvedByTheServer() throws Exception {
		create(owner, BODY)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("회사"))
			.andExpect(jsonPath("$.roadAddress").value("경기 수원시 팔달구 월드컵로 205"))
			.andExpect(jsonPath("$.lat").value(SUWON.lat()))
			.andExpect(jsonPath("$.lng").value(SUWON.lng()));

		List<Workplace> saved = workplaceRepository.findAll();
		assertThat(saved).hasSize(1);
		assertThat(saved.getFirst().getUser().getId()).isEqualTo(owner.getId());
		assertThat(saved.getFirst().getLat()).isEqualTo(SUWON.lat());
	}

	@Test
	void rejectsTheRequestWhenTheAddressCannotBeGeocoded() throws Exception {
		geocoding.result = Optional.empty();

		create(owner, BODY)
			.andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_GEOCODABLE"));

		assertThat(workplaceRepository.count()).isZero();
	}

	@Test
	void reportsBadGatewayWhenTheGeocodingProviderIsDown() throws Exception {
		geocoding.failure = new GeocodingUnavailableException("boom");

		create(owner, BODY)
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"));

		assertThat(workplaceRepository.count()).isZero();
	}

	@Test
	void rejectsABlankName() throws Exception {
		create(owner, """
			{"name":" ","roadAddress":"경기 수원시 팔달구 월드컵로 205"}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(workplaceRepository.count()).isZero();
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/workplaces").contentType(APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

		mockMvc.perform(get("/api/workplaces"))
			.andExpect(status().isUnauthorized());

		assertThat(workplaceRepository.count()).isZero();
	}

	@Test
	void listsOnlyTheCallersOwnWorkplaces() throws Exception {
		create(owner, BODY).andExpect(status().isCreated());

		User stranger = saveUser("stranger@example.com", "google-sub-stranger");

		mockMvc.perform(get("/api/workplaces").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("회사"));

		mockMvc.perform(get("/api/workplaces").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void updatesOnlyTheFieldsPresentInTheRequest() throws Exception {
		UUID id = createdId(owner);

		patchWorkplace(owner, id, """
			{"name":"본사"}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.name").value("본사"))
			.andExpect(jsonPath("$.roadAddress").value("경기 수원시 팔달구 월드컵로 205"))
			.andExpect(jsonPath("$.lat").value(SUWON.lat()));
	}

	@Test
	void regeocodesOnlyWhenTheRoadAddressActuallyChanges() throws Exception {
		UUID id = createdId(owner);
		geocoding.calls = 0;

		patchWorkplace(owner, id, """
			{"roadAddress":"경기 수원시 팔달구 월드컵로 205"}
			""")
			.andExpect(status().isOk());
		assertThat(geocoding.calls).isZero();

		geocoding.result = Optional.of(SEOUL);
		patchWorkplace(owner, id, """
			{"roadAddress":"서울 중구 세종대로 110"}
			""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roadAddress").value("서울 중구 세종대로 110"))
			.andExpect(jsonPath("$.lat").value(SEOUL.lat()))
			.andExpect(jsonPath("$.lng").value(SEOUL.lng()));
		assertThat(geocoding.calls).isEqualTo(1);
	}

	@Test
	void rejectsABlankNameOnUpdate() throws Exception {
		UUID id = createdId(owner);

		patchWorkplace(owner, id, """
			{"name":"   "}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(workplaceRepository.findById(id).orElseThrow().getName()).isEqualTo("회사");
	}

	@Test
	void reportsBadRequestWhenTheNewAddressCannotBeGeocoded() throws Exception {
		UUID id = createdId(owner);
		geocoding.result = Optional.empty();

		patchWorkplace(owner, id, """
			{"roadAddress":"없는 주소"}
			""")
			.andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("ADDRESS_NOT_GEOCODABLE"));

		assertThat(workplaceRepository.findById(id).orElseThrow().getRoadAddress())
			.isEqualTo("경기 수원시 팔달구 월드컵로 205");
	}

	@Test
	void deletesTheCallersOwnWorkplace() throws Exception {
		UUID id = createdId(owner);

		mockMvc.perform(delete("/api/workplaces/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isNoContent());

		assertThat(workplaceRepository.count()).isZero();
	}

	@Test
	void hidesWorkplacesOwnedBySomeoneElseBehindNotFound() throws Exception {
		UUID id = createdId(owner);
		User stranger = saveUser("stranger@example.com", "google-sub-stranger");

		patchWorkplace(stranger, id, """
			{"name":"탈취"}
			""")
			.andExpect(status().isNotFound())
			.andExpect(header().string("Content-Type", "application/problem+json"))
			.andExpect(jsonPath("$.code").value("WORKPLACE_NOT_FOUND"));

		mockMvc.perform(delete("/api/workplaces/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKPLACE_NOT_FOUND"));

		assertThat(workplaceRepository.count()).isEqualTo(1);
		assertThat(workplaceRepository.findById(id).orElseThrow().getName()).isEqualTo("회사");
	}

	@Test
	void reportsNotFoundForAWorkplaceThatDoesNotExist() throws Exception {
		mockMvc.perform(delete("/api/workplaces/{id}", UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, bearer(owner)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WORKPLACE_NOT_FOUND"));
	}

	@Test
	void requiresAuthenticationForUpdateAndDelete() throws Exception {
		UUID id = createdId(owner);

		mockMvc.perform(patch("/api/workplaces/{id}", id)
				.contentType(APPLICATION_JSON)
				.content("""
					{"name":"본사"}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

		mockMvc.perform(delete("/api/workplaces/{id}", id))
			.andExpect(status().isUnauthorized());

		assertThat(workplaceRepository.count()).isEqualTo(1);
	}

	private UUID createdId(User user) throws Exception {
		create(user, BODY).andExpect(status().isCreated());
		return workplaceRepository.findAll().getLast().getId();
	}

	private ResultActions patchWorkplace(User user, UUID id, String body) throws Exception {
		return mockMvc.perform(patch("/api/workplaces/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, bearer(user))
			.contentType(APPLICATION_JSON)
			.content(body));
	}

	private ResultActions create(User user, String body) throws Exception {
		return mockMvc.perform(post("/api/workplaces")
			.header(HttpHeaders.AUTHORIZATION, bearer(user))
			.contentType(APPLICATION_JSON)
			.content(body));
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issue(user, UUID.randomUUID(), Instant.now()).accessToken();
	}

	private User saveUser(String email, String providerId) {
		return userRepository.save(User.builder()
			.provider("google")
			.providerId(providerId)
			.email(email)
			.nickname(providerId)
			.role(UserRole.USER)
			.build());
	}

	/** Lets each test decide what the provider returns without touching the network. */
	static class StubGeocodingClient implements GeocodingClient {

		Optional<Coordinates> result = Optional.empty();
		RuntimeException failure;
		int calls;

		@Override
		public Optional<Coordinates> locate(String roadAddress) {
			calls++;
			if (failure != null) {
				throw failure;
			}
			return result;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubGeocodingConfiguration {

		@Bean
		@Primary
		StubGeocodingClient stubGeocodingClient() {
			return new StubGeocodingClient();
		}
	}
}
