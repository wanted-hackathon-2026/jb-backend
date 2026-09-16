package com.jachwibangjeongsig.jb.me;

import com.jachwibangjeongsig.jb.auth.service.JwtTokenService;
import com.jachwibangjeongsig.jb.user.User;
import com.jachwibangjeongsig.jb.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Expected values come from the approved user/favorite specification, not endpoint implementation. */
@SpringBootTest(properties = {
    "auth.google-client-id=test-google-client-id",
    "auth.access-token-secret=test-access-token-secret-with-at-least-32-bytes",
    "auth.refresh-token-secret=test-refresh-token-secret-with-at-least-32-bytes",
    "vworld.api-key=test-vworld-api-key"
})
@AutoConfigureMockMvc
@Testcontainers
class MyAccountAndFavoriteApiContractTest {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    User me;
    User other;
    UUID propertyId;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void independentEmailAndNicknameUpdatesDoNotOverwriteEachOther(boolean nicknameCommitsLast) {
        var outer = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var inner = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            User stale = users.findById(me.getId()).orElseThrow();
            inner.executeWithoutResult(innerStatus -> {
                User current = users.findById(me.getId()).orElseThrow();
                if (nicknameCommitsLast) current.updateEmail("changed@example.com");
                else current.updateNickname("새닉네임");
                users.flush();
            });
            if (nicknameCommitsLast) stale.updateNickname("새닉네임");
            else stale.updateEmail("changed@example.com");
            users.flush();
        });
        User actual = users.findById(me.getId()).orElseThrow();
        assertThat(actual.getEmail()).isEqualTo("changed@example.com");
        assertThat(actual.getNickname()).isEqualTo("새닉네임");
    }

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM favorite");
        jdbc.update("DELETE FROM property");
        users.deleteAll();
        me = user("me", null);
        other = user("other", "사용중닉네임");
        propertyId = property("테스트 매물");
    }

    @Test
    void myInformationIsIdentifiedByTokenAndIncompleteBeforeNicknameSetup() throws Exception {
        as(me, get("/api/me").param("userId", other.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(me.getId().toString()))
            .andExpect(jsonPath("$.provider").value("GOOGLE"))
            .andExpect(jsonPath("$.email").value("me@example.com"))
            .andExpect(jsonPath("$.nickname").value((Object) null))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.profileCompleted").value(false))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"두자", "가나다라마바사아자차카타파하가"})
    void nicknameAcceptsTwoAndFifteenCharactersAfterTrimming(String nickname) throws Exception {
        nickname("  " + nickname + "  ")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(me.getId().toString()))
            .andExpect(jsonPath("$.nickname").value(nickname))
            .andExpect(jsonPath("$.profileCompleted").value(true));
        User reloaded = users.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo(nickname);
        assertThat(reloaded.getEmail()).isEqualTo("me@example.com");
        assertThat(reloaded.getRole()).isEqualTo(me.getRole());
        as(me, get("/api/me")).andExpect(jsonPath("$.nickname").value(nickname));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "가", "가나다라마바사아자차카타파하가나"})
    void invalidNicknameDoesNotChangeStoredInformation(String nickname) throws Exception {
        problem(nickname(nickname), 400, "INVALID_REQUEST", "/api/me");
        assertThat(users.findById(me.getId()).orElseThrow().getNickname()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nickname\":null}"})
    void nicknameMustBePresentAndNotNull(String body) throws Exception {
        problem(as(me, patch("/api/me").contentType(APPLICATION_JSON).content(body)),
            400, "INVALID_REQUEST", "/api/me");
        assertThat(users.findById(me.getId()).orElseThrow().getNickname()).isNull();
    }

    @Test
    void duplicateNicknameIsRejectedButOwnNicknameCanBeSavedAgain() throws Exception {
        nickname("내닉네임").andExpect(status().isOk());
        problem(nickname("사용중닉네임"), 409, "NICKNAME_ALREADY_EXISTS", "/api/me");
        assertThat(users.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("내닉네임");
        nickname("내닉네임").andExpect(status().isOk());
        assertThat(users.findById(other.getId()).orElseThrow().getNickname()).isEqualTo("사용중닉네임");
    }

    @Test
    void emptyFavoritesReturnAnEmptyPage() throws Exception {
        as(me, get("/api/me/favorites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void favoritesArePagedNewestFirstAndNeverIncludeAnotherUsersFavorites() throws Exception {
        UUID old = favorite(me, propertyId, "2026-09-15 12:00:00");
        UUID newestProperty = property("최근 매물");
        UUID newest = favorite(me, newestProperty, "2026-09-16 12:00:00");
        favorite(other, propertyId, "2026-09-17 12:00:00");
        as(me, get("/api/me/favorites").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].favoriteId").value(newest.toString()))
            .andExpect(jsonPath("$.content[0].property.id").value(newestProperty.toString()))
            .andExpect(jsonPath("$.content[0].property.name").value("최근 매물"))
            .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.content[0].property.address").value("지번 주소"))
            .andExpect(jsonPath("$.content[0].property.roadAddress").value("도로명 주소"))
            .andExpect(jsonPath("$.content[0].property.propertyType").value("원룸"))
            .andExpect(jsonPath("$.content[0].property.leaseType").value("MONTHLY"))
            .andExpect(jsonPath("$.content[0].property.deposit").value(1000))
            .andExpect(jsonPath("$.content[0].property.monthlyRent").value(50))
            .andExpect(jsonPath("$.content[0].property.exclusiveArea").value(23.5))
            .andExpect(jsonPath("$.content[0].property.floor").value(3))
            .andExpect(jsonPath("$.content[0].property.buildYear").value(2020))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.last").value(false));
        as(me, get("/api/me/favorites").param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].favoriteId").value(old.toString()))
            .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "size=0", "size=101", "size=1.5"})
    void invalidPageParametersReturnBadRequest(String query) throws Exception {
        String[] pair = query.split("=");
        problem(as(me, get("/api/me/favorites").param(pair[0], pair[1])),
            400, "INVALID_REQUEST", "/api/me/favorites");
    }

    @Test
    void favoriteDetailUsesPropertyIdAndReturnsPropertyData() throws Exception {
        UUID favoriteId = favorite(me, propertyId, "2026-09-16 12:00:00");
        as(me, get("/api/me/favorites/" + propertyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favoriteId").value(favoriteId.toString()))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.property.id").value(propertyId.toString()))
            .andExpect(jsonPath("$.property.name").value("테스트 매물"))
            .andExpect(jsonPath("$.property.address").value("지번 주소"))
            .andExpect(jsonPath("$.property.roadAddress").value("도로명 주소"))
            .andExpect(jsonPath("$.property.deposit").value(1000))
            .andExpect(jsonPath("$.property.monthlyRent").value(50))
            .andExpect(jsonPath("$.property.exclusiveArea").value(23.5))
            .andExpect(jsonPath("$.property.latitude").value(37.1234))
            .andExpect(jsonPath("$.property.longitude").value(127.1234))
            .andExpect(jsonPath("$.property.sggCode").value("11680"))
            .andExpect(jsonPath("$.property.umdName").value("역삼동"))
            .andExpect(jsonPath("$.property.propertyType").value("원룸"))
            .andExpect(jsonPath("$.property.leaseType").value("MONTHLY"))
            .andExpect(jsonPath("$.property.floor").value(3))
            .andExpect(jsonPath("$.property.totalFloors").value(10))
            .andExpect(jsonPath("$.property.buildYear").value(2020))
            .andExpect(jsonPath("$.property.direction").value("남향"))
            .andExpect(jsonPath("$.property.description").value("매물 설명"));
    }

    @Test
    void anotherUsersFavoriteCannotBeRead() throws Exception {
        favorite(other, propertyId, "2026-09-16 12:00:00");
        problem(as(me, get("/api/me/favorites/" + propertyId)),
            404, "FAVORITE_NOT_FOUND", "/api/me/favorites/" + propertyId);
    }

    @Test
    void registrationCreatesExactlyOneRowAndRejectsDuplicate() throws Exception {
        JsonNode response = json.readTree(register(propertyId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.propertyId").value(propertyId.toString()))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString());
        UUID.fromString(response.get("favoriteId").asText());
        assertThat(countFavorites(me)).isEqualTo(1);
        problem(register(propertyId), 409, "FAVORITE_ALREADY_EXISTS", "/api/me/favorites");
        assertThat(countFavorites(me)).isEqualTo(1);
        assertThat(countFavorites(other)).isZero();
    }

    @Test
    void registrationRejectsNonexistentProperty() throws Exception {
        problem(register(UUID.randomUUID()), 404, "PROPERTY_NOT_FOUND", "/api/me/favorites");
        assertThat(countFavorites(me)).isZero();
    }

    @Test
    void concurrentRegistrationCreatesOnlyOneFavorite() throws Exception {
        String token = tokens.issue(me, UUID.randomUUID(), Instant.now()).accessToken();
        String body = json.writeValueAsString(Map.of("propertyId", propertyId));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
                return mvc.perform(post("/api/me/favorites").header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(countFavorites(me)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"propertyId\":null}", "{\"propertyId\":\"invalid\"}"})
    void registrationRejectsInvalidPropertyId(String body) throws Exception {
        problem(as(me, post("/api/me/favorites").contentType(APPLICATION_JSON).content(body)),
            400, "INVALID_REQUEST", "/api/me/favorites");
        assertThat(countFavorites(me)).isZero();
    }

    @Test
    void deletionIsIdempotentAndDoesNotDeleteOtherUsersFavorite() throws Exception {
        favorite(me, propertyId, "2026-09-16 12:00:00");
        favorite(other, propertyId, "2026-09-16 12:00:00");
        for (int i = 0; i < 2; i++) {
            as(me, delete("/api/me/favorites/" + propertyId))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
            assertThat(countFavorites(me)).isZero();
            assertThat(countFavorites(other)).isEqualTo(1);
        }
    }

    @Test
    void invalidPathIdsReturnBadRequest() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
            get("/api/me/favorites/invalid"), delete("/api/me/favorites/invalid")
        }) {
            problem(as(me, request), 400, "INVALID_REQUEST", "/api/me/favorites/invalid");
        }
    }

    @Test
    void allSixEndpointsRequireAValidAccessToken() throws Exception {
        for (String token : new String[] {null, "invalid-token", tokens.issue(me, UUID.randomUUID(),
            Instant.now().minusSeconds(3600)).accessToken()}) {
            for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/me"), patch("/api/me").contentType(APPLICATION_JSON).content("{\"nickname\":\"두자\"}"),
                get("/api/me/favorites"), get("/api/me/favorites/" + propertyId),
                post("/api/me/favorites").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("propertyId", propertyId))),
                delete("/api/me/favorites/" + propertyId)
            }) {
                if (token != null) request.header("Authorization", "Bearer " + token);
                problem(mvc.perform(request), 401, "INVALID_ACCESS_TOKEN", null);
            }
        }
        assertThat(countFavorites(me)).isZero();
        assertThat(users.findById(me.getId()).orElseThrow().getNickname()).isNull();
    }

    private User user(String identity, String nickname) {
        return users.save(User.builder().provider("google").providerId(identity)
            .email(identity + "@example.com").nickname(nickname).build());
    }

    private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " +
            tokens.issue(user, UUID.randomUUID(), Instant.now()).accessToken()));
    }

    private ResultActions nickname(String value) throws Exception {
        return as(me, patch("/api/me").contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("nickname", value))));
    }

    private ResultActions register(UUID id) throws Exception {
        return as(me, post("/api/me/favorites").contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("propertyId", id))));
    }

    private void problem(ResultActions result, int statusCode, String code, String path) throws Exception {
        result.andExpect(status().is(statusCode))
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.type").isNotEmpty())
            .andExpect(jsonPath("$.title").isNotEmpty())
            .andExpect(jsonPath("$.status").value(statusCode))
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.instance").isNotEmpty())
            .andExpect(jsonPath("$.code").value(code));
        if (path != null) result.andExpect(jsonPath("$.instance").value(path));
    }

    private UUID property(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO property (id, name, address, road_address, sgg_code, umd_name, lat, lng,
                property_type, lease_type, deposit, monthly_rent, exclusive_area, floor, total_floors, build_year,
                direction, description, created_at, updated_at)
            VALUES (?, ?, '지번 주소', '도로명 주소', '11680', '역삼동', 37.1234, 127.1234,
                '원룸', 'MONTHLY', 1000, 50, 23.5, 3, 10, 2020, '남향', '매물 설명',
                '2026-09-16 12:00:00', '2026-09-16 12:00:00')
            """, binary(id), name);
        return id;
    }

    private UUID favorite(User user, UUID property, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO favorite (id, user_id, property_id, created_at) VALUES (?, ?, ?, ?)",
            binary(id), binary(user.getId()), binary(property), createdAt);
        return id;
    }

    private long countFavorites(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE user_id = ?", Long.class, binary(user.getId()));
    }

    private byte[] binary(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
