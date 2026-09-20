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

### 인증

로그인 + 프로필 완료 필요. `AuthConfig`의 `anyRequest().access(profileAuthorizationManager)`에 이미 걸리므로 별도 등록이 필요 없다.

### 요청 본문

| 이름 | 필수 | 타입 | 제약 |
| --- | --- | --- | --- |
| `workplaceId` | 예 | UUID | 요청자 본인의 근무지여야 한다 |
| `transportType` | 예 | enum | `WALK`, `BICYCLE`, `TRANSIT`, `CAR` |
| `maxCommuteMinutes` | 예 | int | 5~180 |
| `sunlightImportance` | 예 | int | 1~5 |
| `quietnessImportance` | 예 | int | 1~5 |
| `safetyImportance` | 예 | int | 1~5 |
| `infrastructureImportance` | 예 | int | 1~5 |
| `depositMin` / `depositMax` | 예 | int | 0 이상, min ≤ max. 단위 만원 |
| `monthlyRentMin` / `monthlyRentMax` | 예 | int | 0 이상, min ≤ max. 단위 만원 |
| `roomTypes` | 예 | string[] | 1~10개, 각 20자 이하, 합쳐서 255자 이하 |

요청 시점의 근무지와 조건은 `recommendation_criteria`에 **스냅샷으로 복사**한다. 근무지나 선호도가 나중에 바뀌어도 지난 추천의 근거는 보존된다.

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
| 400 | (검증 기본) | 요청 본문이 제약을 어김 |
| 404 | `WORKPLACE_NOT_FOUND` | 근무지가 없거나 남의 것 |
| 404 | `RECOMMENDATION_NOT_FOUND` | 추천이 없거나 남의 것, 또는 그 추천에 없는 매물 |
| 409 | `RECOMMENDATION_NOT_READY` | 아직 `COMPLETED`가 아닌 추천의 결과를 조회 |

남의 추천·근무지는 403이 아니라 404로 응답해 존재 자체를 숨긴다.

## 스키마 변경 (`V9__align_recommendation_tables.sql`)

- `recommendation_criteria`: `workplace_address` 삭제, `workplace_road_address`를 `NOT NULL`로. V5에서 `workplace`의 `address`가 사라지고 `road_address`가 필수가 된 것에 스냅샷을 맞춘 것이다.
- `recommendation_result`: 평가 컬럼 추가(`display_order`, `commute_minutes`, `total_score`, 항목별 점수 5종, `summary`).
- `recommendation`: `failure_reason` 추가.
