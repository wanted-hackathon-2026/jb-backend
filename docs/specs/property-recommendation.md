# 매물 추천 명세

## 목적과 근거

- 사용자의 근무지·이동수단·희망 통근시간과 채광/조용함/치안/인프라 중요도, 예산·매물 유형을 받아 조건에 맞는 매물을 추린 뒤, LLM이 항목별 점수와 총평을 매겨 돌려준다.
- 근거: Notion "자취방정식 / API 정의" 데이터베이스의 "매물 추천 (비동기)"(`POST /api/recommendations`), "매물 추천 처리상태 조회"(`GET /api/recommendations/{recommendationId}`), "추천 매물 목록"(`GET /api/recommendations/{recommendationId}/properties`), "조건에 따른 추천 매물 상세"(`GET /api/recommendations/{recommendationId}/properties/{propertyId}`) 항목(모두 상태: 시작 전)과 현재 코드(`V2__create_core_domain_tables.sql`의 `recommendation`·`recommendation_criteria`·`recommendation_result`, `PropertyFeature`, `Workplace`, `LlmClient`).
- Notion 네 페이지는 경로·메서드와 한 줄짜리 요청/응답 설명만 있고 필드 수준 정의가 비어 있다. 아래 요청·응답 필드와 상태 전이, 오류 코드는 **이 문서에서 새로 정한 것**이다.

## 비동기인 이유

LLM 호출이 수십 초 걸린다. `POST`는 접수만 하고 `202 Accepted`로 `recommendationId`와 `PENDING`을 즉시 돌려준 뒤, 처리는 애플리케이션 내부 비동기 실행기에서 이어간다. 프런트는 상태 조회를 폴링해 `COMPLETED`가 되면 결과를 가져온다.

> 한계: 인메모리 실행이라 처리 도중 앱이 죽으면 그 추천은 `PROCESSING`에 멈춘 채 남는다. 재시작 시 오래 멈춘 건을 `FAILED`로 쓸어 담는 작업이나 외부 큐는 이번 범위 밖이다.

## 상태 전이

`PENDING` → `PROCESSING` → `COMPLETED` | `FAILED`

- `FAILED`면 `failureReason`에 사유 요약이 담긴다(최대 255자).
- 조건에 맞는 매물이 **하나도 없으면 실패가 아니다.** 빈 결과로 `COMPLETED` 처리한다.

## 1. `POST /api/recommendations` — 추천 요청

### 인증 — 로그인·비로그인 모두 허용

추천 4개 엔드포인트는 `AuthConfig`에 `permitAll()`로 등록한다. 기본 게이트 `ProfileAuthorizationManager`가 인증 + 프로필 완료를 동시에 요구해서, 그대로 두면 비로그인이 전부 401이 된다.

근거: 회의 3 — "비로그인 상태에서 사용자 선호도 입력 후 AI 추천 요청 가능해야 함", "비로그인 세션 처리는 데모 버전에도 필수". `recommendation` 테이블의 `chk_recommendation_owner`(user_id XOR client_session_id)가 처음부터 이걸 전제로 설계돼 있다.

| 조건 | 소유자 |
| --- | --- |
| 유효한 `Authorization: Bearer` | `recommendation.user_id` |
| 토큰 없음 + `X-Client-Session` 헤더 | `recommendation.client_session_id` |
| 토큰 없음 + 헤더 없음 | `400 CLIENT_SESSION_REQUIRED` |

`X-Client-Session`은 **클라이언트가 세션스토리지에 만든 랜덤 UUID**다(회의 3: "세션 스토리지 + 랜덤 UUID"). 서버는 **SHA-256 해시만** `client_session.session_hash_token`에 저장한다 — `refresh_token_session`과 같은 방식이라 DB가 유출돼도 남의 추천을 조회할 원본 토큰이 나오지 않는다.

- 세션 행은 **추천 요청 때만** 만든다. 조회는 이미 있는 세션만 인정하고, 모르는 토큰이면 404로 떨어진다 — 조회만으로 세션이 쌓이면 아무나 행을 만들 수 있다.
- `expires_at`은 발급 시 +30일. 만료 행 정리 배치는 범위 밖이다.
- **CORS**: `X-Client-Session`은 기본 허용 헤더가 아니라서 `AuthConfig`의 `allowedHeaders`에 넣어야 한다. 빠뜨리면 브라우저가 프리플라이트에서 헤더를 떨어뜨려 비로그인 요청이 전부 `CLIENT_SESSION_REQUIRED`로 실패한다.
- **알려진 함정**: permitAll 경로라도 **만료·위조된 `Authorization` 헤더를 보내면 리소스 서버 필터가 401을 낸다** — 익명으로 강등되지 않는다(`property-listing-and-detail.md`에 같은 내용이 있다). 프런트는 로그아웃 상태에서 `Authorization` 헤더를 아예 붙이지 않아야 한다.
- 비로그인 소유권은 랜덤 UUID를 아는 사람이면 통과한다. 로그인 계정 수준의 보호가 아니며, 그래서 비로그인 추천에는 저장된 프로필 정보가 들어가지 않는다(거점 주소는 사용자가 방금 입력한 값일 뿐이다).

### 요청 본문

| 이름 | 필수 | 타입 | 제약 |
| --- | --- | --- | --- |
| `workplaceId` | 택1 | UUID | 저장된 근무지. 요청자 본인의 것이어야 한다 |
| `workplace` | 택1 | object | 이번 요청에만 쓸 근무지. `{name}`(1~50자), `{roadAddress}`(1~255자) |
| `transportType` | 예 | enum | `WALK`, `BICYCLE`, `TRANSIT`, `CAR` |
| `maxCommuteMinutes` | 예 | int | 5~180 |
| `sunlightImportance` | 예 | int | 1~5 |
| `quietnessImportance` | 예 | int | 1~5 |
| `safetyImportance` | 예 | int | 1~5 |
| `infrastructureImportance` | 예 | int | 1~5 |
| `depositMin` / `depositMax` | 예 | int | 0 이상, min ≤ max. 단위 만원 |
| `monthlyRentMin` / `monthlyRentMax` | 예 | int | 0 이상, min ≤ max. 단위 만원 |
| `roomTypes` | 예 | string[] | 1~10개, 각 20자 이하, 합쳐서 255자 이하 |

**`workplaceId`와 `workplace` 중 정확히 하나만 보낸다.** 둘 다 보내거나 둘 다 빠지면 `400`이다.

- **비로그인 사용자는 `workplace`만 쓸 수 있다.** `workplace` 테이블은 `user_id`가 필수라 거점을 저장할 방법이 없다. 비로그인 요청이 `workplaceId`를 보내면 남의 거점과 똑같이 `404 WORKPLACE_NOT_FOUND`로 존재를 감춘다.
- 로그인 사용자는 둘 다 쓸 수 있다. 저장하지 않고 한 번만 시험해 보는 주소가 있기 때문이다.
- `workplace`를 쓰면 **서버가 `roadAddress`를 지오코딩해 좌표를 확정한다.** 좌표를 요청으로 받지 않는 것은 매물·거점 등록과 같은 규칙이다 — 클라이언트가 좌표를 위조하면 통근 반경 필터를 우회할 수 있다.

요청 시점의 근무지와 조건은 `recommendation_criteria`에 **스냅샷으로 복사**한다. 근무지나 선호도가 나중에 바뀌어도 지난 추천의 근거는 보존된다. 스키마가 근무지를 FK가 아니라 컬럼으로 들고 있는 것도 비로그인 사용자에게 거점 행이 없기 때문이다.

### 응답

`202 Accepted` — `{ "recommendationId": "...", "status": "PENDING" }`

## 2. `GET /api/recommendations/{recommendationId}` — 처리상태 조회

`200 OK` — `recommendationId`, `status`, `requestedAt`, `startedAt`, `completedAt`, `failureReason`.

## 3. `GET /api/recommendations/{recommendationId}/properties` — 추천 매물 목록

`COMPLETED`일 때만 응답한다. `content` 배열은 `evaluation.rank` 오름차순이며, 항목마다 매물 요약(`id`, `name`, `roadAddress`, `latitude`, `longitude`, `thumbnailUrl`, `propertyType`, `leaseType`, `deposit`, `monthlyRent`, `exclusiveArea`, `floor`)과 `evaluation`이 붙는다.

`evaluation`: `rank`, `commuteMinutes`, `totalScore`, `sunlightScore`, `quietnessScore`, `safetyScore`, `infrastructureScore`, `commuteScore`, `summary`. 점수는 모두 0~100 정수다.

## 4. `GET /api/recommendations/{recommendationId}/properties/{propertyId}` — 추천 매물 상세

`{ "property": <매물 상세 조회와 동일한 응답>, "evaluation": { ... } }`. 그 추천에 포함되지 않은 매물이면 404다.

## 후보 선정 (결정 — Notion에 없어 이번 문서에서 정함)

1. 근무지 좌표에서 `transportType`의 실효 속도 × `maxCommuteMinutes` 만큼을 반경으로 잡는다.
2. 그 반경의 외접 사각형 + 예산 범위 + 매물 유형으로 DB에서 후보를 뽑는다.
3. 직선거리를 다시 계산해 통근 한도를 넘는 모서리 매물을 걷어낸다.
4. 통근시간 오름차순으로 **최대 15개**만 LLM에 넘긴다. 토큰 비용과 응답 시간을 묶어 두기 위한 상한이다.

> **통근시간은 직선거리 근사다.** 실제 대중교통 소요시간이 아니다. 이동수단별 실효 속도(`TransportType`)는 우회·환승·대기를 뭉뚱그려 보정한 값이며, 길찾기 API를 붙이면 그 소요시간으로 교체한다. 응답의 `commuteMinutes`도 같은 근사값이다.

## 모델 출력 취급

`LlmClient`의 구조화 출력(strict `json_schema`)으로 형태는 강제되지만 내용은 신뢰 경계 밖이다.

- 존재하지 않는 후보 번호, 빈 총평, 중복 번호는 버린다.
- 점수는 0~100으로 잘라 저장한다.
- 쓸 수 있는 평가가 하나도 남지 않으면 `FAILED`로 기록한다.
- 순위는 모델이 매긴 `totalScore` 내림차순으로 다시 매긴다.

## 오류

| 상태 | 코드 | 상황 |
| --- | --- | --- |
| 400 | (검증 기본) | 요청 본문이 제약을 어김. `workplaceId`/`workplace` 동시 지정·동시 누락 포함 |
| 400 | `CLIENT_SESSION_REQUIRED` | 비로그인인데 `X-Client-Session` 헤더가 없음 |
| 400 | `ADDRESS_NOT_GEOCODABLE` | 인라인 근무지 주소의 좌표를 찾지 못함 |
| 404 | `WORKPLACE_NOT_FOUND` | 근무지가 없거나 남의 것. 비로그인이 `workplaceId`를 보낸 경우 포함 |
| 404 | `RECOMMENDATION_NOT_FOUND` | 추천이 없거나 남의 것, 또는 그 추천에 없는 매물 |
| 409 | `RECOMMENDATION_NOT_READY` | 아직 `COMPLETED`가 아닌 추천의 결과를 조회 |
| 502 | `GEOCODING_UNAVAILABLE` | 지오코딩 제공자 장애 |

남의 추천·근무지는 403이 아니라 404로 응답해 존재 자체를 숨긴다.

## 비로그인 지원에 필요한 스키마 변경 — 없음

`client_session` 테이블과 `recommendation.client_session_id`(FK + `chk_recommendation_owner` CHECK)는 **`V2`부터 이미 있었다.** 엔티티가 `user_id`를 `nullable = false`로 잡고 있어 스키마보다 좁았을 뿐이라, 매핑만 넓히면 된다. 새 마이그레이션을 추가하지 않는다.

## 스키마 변경 (`V9__align_recommendation_tables.sql`)

- `recommendation_criteria`: `workplace_address` 삭제, `workplace_road_address`를 `NOT NULL`로. V5에서 `workplace`의 `address`가 사라지고 `road_address`가 필수가 된 것에 스냅샷을 맞춘 것이다.
- `recommendation_result`: 평가 컬럼 추가(`display_order`, `commute_minutes`, `total_score`, 항목별 점수 5종, `summary`).
- `recommendation`: `failure_reason` 추가.
