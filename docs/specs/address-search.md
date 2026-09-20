# 주소 검색 명세

## 목적과 근거

- 거점 등록·수정 화면에서 사용자가 주소 일부를 입력하면 후보 주소 목록을 보여주고, 그중 하나를 고르게 한다. 고른 주소를 그대로 `POST/PATCH /api/workplaces`에 넘긴다.
- 근거: Notion "자취방정식 / API 정의"의 "주소 검색"(`GET /api/address/search`, `GET`, 상태: 시작 전) 항목과 회의 3 결정 — **"주소 검색은 외부 API 연동 사용 예정, 우리 API를 경유하는 구조"**.
- **Notion 페이지 본문이 비어 있어 요청·응답 스펙이 없다.** 아래 내용은 이번 명세에서 새로 정한 것이다.

### 제공자 확정

- **VWorld 검색 API(`https://api.vworld.kr/req/search`)를 쓴다.** 사용자 확인으로 결정했다.
- 이미 `VWORLD_API_KEY`가 `application.yaml`·`.env.example`·서버 `.env`에 있고 `VWorldGeocodingClient`가 같은 키를 쓴다. **환경변수 추가 작업이 필요 없다** — 카카오를 쓰면 `KAKAO_REST_KEY`를 서버 `.env` → `compose.prod.yaml` → `application.yaml` 순으로 먼저 넣어야 했다.
- VWorld 검색은 후보마다 좌표(`point`)를 함께 주므로, 프런트가 주소를 고른 뒤 좌표를 얻으려고 한 번 더 호출할 필요가 없다.

### 기존 지오코딩과의 관계

- `GeocodingClient.locate()`(`/req/address`, `request=getCoord`)는 **완성된 도로명주소 하나 → 좌표 하나**다. 거점 저장 시 서버가 좌표를 확정하는 용도로 계속 쓴다.
- 이번에 추가하는 것은 **부분 문자열 → 후보 목록**이라 엔드포인트도 응답 형태도 다르다. 별도 클라이언트로 둔다.
- 검색 결과의 좌표를 그대로 믿고 저장하지는 않는다. 거점 저장 경로는 지금처럼 서버가 `locate()`로 다시 확정한다(클라이언트가 좌표를 위조해 보낼 수 없게 하는 기존 규칙 유지).

## `GET /api/address/search`

### 인증

- **로그인 불필요.** `AuthConfig`에 `GET /api/address/search`를 `permitAll()`로 추가해야 한다(현재 `anyRequest().access(profileAuthorizationManager)`라 그대로 두면 로그인+프로필 완료를 요구한다).
- 근거: Notion "주소 검색" 행의 `로그인 상태`가 비어 있다(`필수`가 아니다). 회의 3에서 **비로그인 사용자도 선호도를 입력해 AI 추천을 요청할 수 있어야 한다**고 정했고, 그 입력에 거점 주소가 들어간다.

### 요청 파라미터 (query string)

| 이름 | 필수 | 타입 | 설명 |
| --- | --- | --- | --- |
| `query` | 예 | string | 검색어. 앞뒤 공백 제거 후 2~100자 |
| `page` | 아니오 | int | 1부터 시작하는 페이지 번호. 기본값 1, 1~100 |

### 검증 규칙

1. `query`는 앞뒤 공백을 제거한 뒤 2자 미만이면 `400 INVALID_REQUEST`. 한 글자로는 후보가 너무 많아 의미 있는 결과가 나오지 않는다.
2. `page`는 1 이상 100 이하의 정수만 허용한다.
3. **페이지 크기는 10으로 서버가 고정한다.** 클라이언트가 정할 수 없다 — 외부 API 호출량을 예측 가능하게 두려는 것이고, 필요해지면 그때 파라미터로 연다.

### 검색 범위 (결정)

- VWorld `type=address`, `category=road` — **도로명주소만** 검색한다. 거점 저장이 `roadAddress`를 받으므로 지번만 있는 후보는 그대로 쓸 수 없다.
- 응답의 `jibunAddress`는 같은 후보의 지번 표기(`address.parcel`)로, 사용자가 후보를 알아보기 쉽게 화면에 같이 보여주는 용도다. 저장에는 쓰지 않는다.

### 응답

`200 OK`

```json
{
  "addresses": [
    {
      "roadAddress": "경기도 수원시 팔달구 월드컵로 205",
      "jibunAddress": "경기도 수원시 팔달구 우만동 228",
      "latitude": 37.279609852101984,
      "longitude": 127.04339808904444
    }
  ],
  "page": 1,
  "hasMore": true
}
```

- `hasMore`: VWorld 응답의 `page.current < page.total`이면 `true`. 프런트가 "더 보기"를 붙일지 판단하는 데만 쓴다. 전체 건수는 노출하지 않는다 — 외부 API가 주는 `record.total`은 근사치라 신뢰도가 낮고, 화면에 필요하지도 않다.
- **결과가 없으면 `{"addresses": [], "page": 1, "hasMore": false}`를 `200`으로 준다.** VWorld가 `status: NOT_FOUND`를 주는 경우도 여기에 해당하며, 오류가 아니다.
- `jibunAddress`: VWorld가 지번 표기를 주지 않으면 JSON `null`.

### 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | `query` 누락, 공백 제거 후 2자 미만/100자 초과, `page` 범위 밖이거나 정수가 아님 |
| 502 | `ADDRESS_SEARCH_UNAVAILABLE` | VWorld가 `OK`/`NOT_FOUND`가 아닌 상태를 주거나, 타임아웃·연결 실패·빈 본문 |

- 지오코딩 쪽 `GEOCODING_UNAVAILABLE`과 코드를 나눈다. 둘 다 VWorld지만 엔드포인트가 달라 한쪽만 죽을 수 있고, 프런트 대응도 다르다(검색은 재시도 안내, 저장은 주소 재선택 안내).
- **외부 키(`VWORLD_API_KEY`)나 VWorld 원본 오류 메시지는 응답에 넣지 않는다.** 로그에만 남긴다.

## 범위 밖

- 검색 결과 캐싱. 회의 3의 "외부 API를 매번 호출하지 않고 DB에 저장" 결정은 **매물 등록 시점의 지표 데이터**에 대한 것이고, 사용자가 치는 검색어는 캐시 적중률이 낮아 이번에는 넣지 않는다.
- 호출량 제한(rate limit). 지금 다른 외부 연동에도 없다.
- 지번주소 검색(`category=parcel`), 건물명 검색(`type=place`). 필요해지면 파라미터로 연다.
