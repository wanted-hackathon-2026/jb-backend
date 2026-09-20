# 매물 목록·상세 조회 명세

## 목적과 근거

- 지도 화면에서 현재 보이는 영역 안의 매물을 마커로 보여주고(목록 조회), 마커를 클릭하면 매물 상세 화면에 필요한 정보와 사진 전체를 보여준다(상세 조회).
- 근거: Notion "자방-개인 / API 정의 (1)" 데이터베이스의 "매물 조회"(`GET /api/properties/map`, 상태: 시작 전), "매물 상세 조회"(`GET /api/properties/{Id}`, 상태: 시작 전) 항목과 현재 코드(`Property`, `PropertyImage`, `Favorite` 엔티티·리포지토리, `PropertyController`, `AuthConfig`, `docs/specs/property-image-registration.md`).
- 아직 구현되지 않은 기능이며, 이 문서는 구현 전 명세만 다룬다. 코드·테스트는 이 문서에 포함하지 않는다.
- **경로 확정**: Notion "매물 조회" 페이지의 데이터베이스 속성 `API` 값은 `/api/properties`이지만, 같은 페이지 본문 제목은 `## GET /api/properties/map`으로 되어 있어 서로 불일치했다. 사용자 확인 결과 **본문 기준인 `/api/properties/map`을 목록 조회 경로로 확정**한다. `POST /api/properties`(매물 등록, 관리자 전용)와 경로가 섞이지 않도록 목록 조회는 별도 하위 경로를 쓴다.
- **2026-09-20 확장**: 실제 배포된 프런트(jb-front.kaameo12.workers.dev) 목록·상세 화면 스크린샷을 대조해, Notion 응답 예시에는 없었지만 화면에 실제로 쓰이는 필드 3종을 추가로 확인함. 아래 "2차 확장" 절 참고.

## 1. `GET /api/properties/map` — 지도 영역 내 매물 목록 조회

### 인증

- 로그인 불필요. `AuthConfig`에 `GET /api/properties/map`을 `permitAll()`로 추가해야 한다(현재는 `POST /api/properties`만 명시돼 있고, 나머지는 `.anyRequest().access(profileAuthorizationManager)`로 로그인+프로필 완료를 요구하므로 그대로 두면 이 엔드포인트도 로그인을 요구하게 된다).
- 일반 지도 탐색과 AI 추천 전 탐색 화면에서 공통으로 사용한다(Notion 근거).

### 요청 파라미터 (query string)

| 이름 | 필수 | 타입 | 설명 |
| --- | --- | --- | --- |
| `minLat` | 예 | double | 지도 영역의 남쪽(최소) 위도 |
| `maxLat` | 예 | double | 지도 영역의 북쪽(최대) 위도 |
| `minLng` | 예 | double | 지도 영역의 서쪽(최소) 경도 |
| `maxLng` | 예 | double | 지도 영역의 동쪽(최대) 경도 |
| `limit` | 아니오 | int | 반환할 최대 매물 수. 기본값 100, 1~200 |

### 검증 규칙

1. `minLat`, `maxLat`, `minLng`, `maxLng`는 각각 WGS84 유효 범위(위도 -90~90, 경도 -180~180)를 벗어나면 안 된다.
2. `minLat <= maxLat`, `minLng <= maxLng`여야 한다. **경도 180도/−180도 경계를 넘나드는(태평양을 가로지르는) 영역은 지원하지 않는다** — 서비스 대상이 대한민국이라 날짜변경선 wraparound은 이번 범위에서 고려하지 않는다.
3. `limit`은 1 이상 200 이하의 정수만 허용한다.

### 정렬·건수 제한 (결정 — Notion에 명시되지 않아 이번 문서에서 정함)

- **전통적인 페이지네이션(`page`/`size`, `totalElements` 등)을 사용하지 않는다.** 프런트는 지도 이동이 끝날 때마다 새 `minLat/maxLat/minLng/maxLng`로 이 API를 다시 호출하는 것을 전제로 한다(Notion "결정" 항목).
- 영역 안에 매물이 `limit`보다 많으면 **`createdAt` 내림차순(최근 등록순)으로 정렬한 뒤 `limit`개까지만 자른다.** 이 정렬 기준은 Notion 문서에 없어 이번 명세에서 새로 정한 것이며, 잘림 여부를 알려주는 필드(`hasMore` 등)는 포함하지 않는다 — 지도 화면에서는 영역을 좁히거나 확대하는 것으로 충분하다고 보되, 사용자가 다른 기준(거리순 등)을 원하면 별도 승인 후 반영한다.
- 지도 마커 클러스터링은 프런트에서 카카오맵 클러스터러로 처리하며, 이 API는 클러스터링 결과를 계산하지 않는다(Notion "결정" 항목).

### 응답

`200 OK`

```json
{
  "properties": [
    {
      "id": "매물 UUID",
      "latitude": 37.0,
      "longitude": 127.0,
      "thumbnailUrl": "/api/property-images/{propertyId}/{storedFilename}",
      "name": "봉천동 원룸",
      "propertyType": "원룸",
      "leaseType": "MONTHLY",
      "deposit": 3000,
      "monthlyRent": 45,
      "exclusiveArea": 23.1,
      "floor": 2,
      "address": "서울특별시 관악구 봉천동",
      "favorite": false
    }
  ]
}
```

- 상세 지표(인프라·치안·소음·채광 등 `property_feature`)와 사진 전체 목록, 도로명주소는 반환하지 않는다(Notion "결정" 항목: 상세 화면에서만 제공).
- `thumbnailUrl`: 해당 매물의 `displayOrder = 0`인 사진의 조회 URL(`docs/specs/property-image-registration.md`의 URL 형식과 동일). **사진이 한 장도 등록되지 않았으면 `thumbnailUrl`은 JSON `null`이다.** (Notion 예시의 "대표 사진 URL 또는 null"을 그대로 따름)
- 매물이 0건이면 `{"properties": []}`를 반환한다(오류 아님).
- `exclusiveArea`, `floor`는 매물 등록 시 선택 입력이라 값이 없으면 JSON `null`이다. 프로젝트에 별도 null 생략 설정이 없으므로 필드 자체는 항상 포함되고 값만 `null`일 수 있다.
- `favorite`(2026-09-20 추가): 상세 조회와 **동일한 선택적 인증 규칙**을 따른다 — `Authorization` 헤더가 없으면 목록의 모든 항목이 `favorite: false`, 유효한 토큰이 있으면 그 사용자의 즐겨찾기 여부를 매물별로 계산한다. `supplyArea`/`bathroomCount`는 목록 화면 목업에 없어 **목록 응답에는 추가하지 않았다** — 상세 응답에만 있다.

### 오류

| 상태 | code | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수 파라미터 누락, 숫자로 변환할 수 없는 값, `limit`이 1~200 범위 밖(기존 전역 검증 오류 처리 재사용) |
| 400 | `INVALID_MAP_BOUNDS` | 위도·경도가 유효 범위를 벗어남, 또는 `minLat > maxLat` / `minLng > maxLng`(새 코드, `PropertyExceptionHandler`에 추가 필요) |

## 2. `GET /api/properties/{propertyId}` — 매물 상세 조회

### 인증

- 로그인 불필요(Notion: "비로그인 사용자도 조회할 수 있다"). `AuthConfig`에 `GET /api/properties/*`를 `permitAll()`로 추가해야 한다.
- **`favorite` 필드는 선택적 인증으로 계산한다(결정 — Notion에 세부 규칙 없음)**:
  - `Authorization: Bearer <accessToken>` 헤더가 없으면 `favorite: false`.
  - 헤더가 있고 유효한 access token이며 대응하는 사용자가 DB에 존재하면, 기존 `FavoriteRepository.existsByUserIdAndPropertyId(userId, propertyId)`로 실제 값을 계산한다.
  - 헤더는 있지만 토큰이 만료·위조되었으면 익명으로 간주해 `favorite: false`로 응답하는 것이 이 문서의 의도다. 다만 **Spring Security의 OAuth2 리소스 서버 필터는 `permitAll` 여부와 무관하게 Bearer 토큰이 오면 먼저 검증하므로, 실제로는 만료·위조된 토큰이 함께 오면 401이 반환될 수 있다.** 이 필터 동작을 명세 수준에서 무시하지 않기 위해 남겨두며, 완전한 익명 폴백이 필요하면 별도 구현 방식(예: 이 경로만 리소스 서버 필터에서 제외하고 컨트롤러에서 직접 토큰을 파싱)을 사용자 승인 후 정한다.
  - 프로필 미완료 사용자(닉네임 미설정)도 조회는 가능해야 하므로 이 경로에는 `profileAuthorizationManager`를 적용하지 않는다.

### 요청

- Path: `propertyId` — 매물 UUID

### 응답

`200 OK`

```json
{
  "id": "매물 UUID",
  "name": "봉천동 원룸",
  "address": "서울특별시 관악구 봉천동 919-19",
  "roadAddress": "서울특별시 관악구 봉천로 1",
  "latitude": 37.0,
  "longitude": 127.0,
  "propertyType": "원룸",
  "leaseType": "MONTHLY",
  "deposit": 3000,
  "monthlyRent": 45,
  "exclusiveArea": 23.1,
  "supplyArea": 33.0,
  "floor": 2,
  "bathroomCount": 1,
  "totalFloors": 5,
  "buildYear": 2020,
  "direction": "남향",
  "description": "매물 설명",
  "images": [
    {
      "id": "사진 UUID",
      "url": "/api/property-images/{propertyId}/{storedFilename}",
      "displayOrder": 0
    }
  ],
  "favorite": false
}
```

- `sggCode`, `umdName`, `createdAt`, `updatedAt`은 포함하지 않는다 — Notion 응답 예시에 없고, 관리자용 등록 응답(`PropertyResponse`)과는 다른 목적의 공개용 DTO이기 때문이다. 관리자 화면에서 이 값이 필요하면 별도 API로 다룬다(이번 범위 아님).
- `images`는 `displayOrder` 오름차순으로 반환한다. **사진이 없으면 `images: []`를 반환한다** (Notion 명시). 첫 번째 원소(있다면 `displayOrder = 0`)가 대표 사진이다.
- `exclusiveArea`, `supplyArea`, `floor`, `bathroomCount`, `totalFloors`, `buildYear`, `direction`, `description`은 등록 시 선택 입력이므로 값이 없으면 해당 필드는 JSON `null`이다.
- `property_feature`에 저장된 인프라·치안·소음·채광 지표는 이 응답에 포함하지 않는다(Notion 응답 예시에 없음, 추천 단계에서 별도로 다룸).
- `supplyArea`(공급면적, ㎡)·`bathroomCount`(욕실 수, 2026-09-20 추가): Notion 응답 예시엔 없었지만 실제 배포 프런트 상세 화면("전용/공급면적", "구조/욕실 수")에 쓰이는 걸 확인해 추가함. `POST /api/properties` 등록 요청에도 선택 필드로 함께 추가했다(`docs/specs/property-registration.md` 참고).

### 오류

| 상태 | code | 조건 |
| --- | --- | --- |
| 404 | `PROPERTY_NOT_FOUND` | `propertyId`에 해당하는 매물이 없음(`docs/specs/property-image-registration.md`와 동일한 코드 재사용) |

## 2차 확장 (2026-09-20) — 프런트 목업 대조

실제 배포된 프런트(jb-front.kaameo12.workers.dev)의 목록/상세 화면 스크린샷을 받아 1차 명세(Notion 응답 예시 그대로)와 대조한 결과, 화면에는 있는데 API엔 없던 필드가 3종류 있었다. 이 중 아래 2종은 이번에 추가로 구현함:

1. **`bathroomCount`(욕실 수), `supplyArea`(공급면적)** — 상세 화면에 "구조/욕실 수: 분리형 원룸 / 1개", "전용/공급면적: 6평 / 10평"으로 표시됨. `Property` 테이블에 `bathroom_count`, `supply_area` 컬럼을 추가(`V8__add_supply_area_and_bathroom_count.sql`)하고 등록 API(`POST /api/properties`)와 상세 조회 응답에 반영함. **목록 화면에는 이 두 값이 안 보여서 목록 응답에는 추가하지 않았다.**
2. **목록 항목별 `favorite`** — 목록 카드 사진에 즐겨찾기 하트 아이콘이 매물마다 다르게 표시되는 걸 확인해서, 상세와 동일한 선택적 인증 규칙으로 목록에도 `favorite`을 추가함.

다음 1종은 **이번에 구현하지 않음** — 데이터 소스가 아직 없어서 추측으로 만들지 않기로 함:

3. **지하철 호선명 표시** ("2호선·1호선" 같은 텍스트) — 목록 화면 카드에 나온다. 현재 `property_feature`에는 카카오 로컬 API로 구한 `NEAREST_SUBWAY_STATION_DISTANCE`(최근접 역까지 거리, 숫자)만 있고 **호선 번호/이름을 저장하는 데가 전혀 없다.** 카카오 카테고리 검색(`SW8`) 응답의 `place_name`은 역 이름만 주고 호선 정보를 구조화된 필드로 주지 않아서, 이 기능을 넣으려면 (a) 별도의 역-호선 매핑 데이터셋(예: 서울 열린데이터광장의 지하철역 정보, `data/infrastructure/bus-stops.csv`처럼 CSV로 반입) 확보, (b) 매물 주변 반경 내 여러 역을 조회해 호선들을 합치는 로직, (c) 저장 위치(`property_feature`에 새 지표로 넣을지, 목록/상세 응답 전용 계산으로만 둘지) 결정이 먼저 필요하다. 실제 데이터 소스 없이 임의로 구현하지 않았다.

## 공통 사항

- 서울 매물만이 아니라 전국 매물을 모두 대상으로 한다(등록 API가 `sggCode`를 서울로 제한하지 않으므로, 조회 API도 지역으로 결과를 제한하지 않는다).
- 매물 삭제·비활성화 개념이 현재 엔티티에 없으므로, 등록된 매물은 전부 조회 대상이다.
- 응답 오류는 기존 공통 형식(`Content-Type: application/problem+json`, 필드 `type`/`title`/`status`/`detail`/`instance`/`code`/`timestamp`)을 그대로 따른다.
- 좌표 필드명은 `latitude`/`longitude`로 기존 즐겨찾기·매물 등록 응답과 통일한다.

## 명세 테스트 대상 (구현 시 실패하는 테스트부터 작성)

1. 목록: bbox 안의 매물만 반환하고 밖의 매물은 제외한다(경계값 포함 여부도 확인 — 등호 포함으로 간주).
2. 목록: 사진이 없는 매물은 `thumbnailUrl: null`, 대표 사진(`displayOrder = 0`)이 있는 매물은 그 URL을 반환한다.
3. 목록: `minLat > maxLat` 또는 좌표 범위 밖 값은 `400 INVALID_MAP_BOUNDS`, 파라미터 누락/타입 오류는 `400 INVALID_REQUEST`.
4. 목록: `limit`을 초과하는 매물이 있으면 최근 등록순으로 `limit`개까지만 반환한다. `limit` 생략 시 기본값 100이 적용된다.
5. 목록: 매물이 0건이어도 `200`과 빈 배열을 반환한다(오류 아님).
6. 상세: 사진이 여러 장이면 `displayOrder` 오름차순으로 반환하고, 없으면 `images: []`.
7. 상세: 존재하지 않는 `propertyId`는 `404 PROPERTY_NOT_FOUND`.
8. 상세: Authorization 헤더 없이 호출해도 `200`과 `favorite: false`를 반환한다(로그인 필요 없음).
9. 상세: 로그인한 사용자가 즐겨찾기한 매물을 조회하면 `favorite: true`, 즐겨찾기하지 않았으면 `favorite: false`.
10. 두 엔드포인트 모두 `AuthConfig`의 `permitAll` 설정이 실제로 적용되어, 미인증 요청이 401로 막히지 않는지 확인한다.
11. 상세: `supplyArea`, `bathroomCount`가 있으면 그 값을, 없으면 `null`을 반환한다.
12. 목록: 로그인한 사용자가 즐겨찾기한 매물은 목록 항목의 `favorite: true`, 아니면 `false`. 비로그인 호출은 모든 항목이 `false`.

## 이번 범위에서 제외

- 가격·평수·매물종류 등 필터링, 검색어 검색.
- 전통적인 페이지 기반 페이지네이션과 정렬 옵션 선택.
- `property_feature`(인프라·치안·소음·채광) 지표를 목록·상세 응답에 포함하는 것.
- 지도 마커 클러스터링 로직(프런트 책임).
- 매물 수정·삭제 API.
- 만료/위조된 토큰이 함께 온 비로그인성 상세 조회를 완전히 관대하게 처리하는 보안 필터 변경(위 "인증" 절의 알려진 제약으로 남겨둠).
- **지하철 호선명 표시**(위 "2차 확장" 3번). 데이터 소스·저장 위치가 정해지지 않아 이번엔 구현하지 않음.
- `supplyArea`/`bathroomCount`의 목록 응답 포함. 목록 화면 목업에 없어서 상세 응답에만 추가함.

## 미확정 사항 (구현 전 재확인 필요)

- `INVALID_MAP_BOUNDS`라는 코드명 자체는 이번 문서에서 새로 제안한 것이다. 기존 코드 네이밍 관례(`PROPERTY_NOT_FOUND`, `INVALID_PROPERTY_IMAGE` 등)와 맞는지 확인이 필요하다.
- 목록 API가 `limit` 초과 시 "최근 등록순 정렬 후 자르기"를 선택했는데, 이 기준이 실제 프런트 요구사항(예: 지도 중심에서 가까운 순)과 맞는지 확인이 필요하다.
- 상세 API에서 만료된 토큰이 왔을 때 401 대신 비로그인으로 처리해야 하는지(추가 구현 필요) 여부.
- **지하철 호선명**: 역-호선 매핑 데이터를 어디서 구할지(서울 열린데이터광장 등), 반경을 얼마로 잡을지, `property_feature`에 새 지표로 저장할지 여부를 사용자가 확정해야 다음 작업을 시작할 수 있다.
