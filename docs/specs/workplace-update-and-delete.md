# 거점(회사 등) 수정·삭제 명세

## 목적과 근거

- 사용자가 등록한 거점의 이름·주소를 고치고, 더 이상 쓰지 않는 거점을 지운다.
- 근거: Notion "자취방정식 / API 정의" 데이터베이스의 "거점(회사 등) 수정"(`PATCH /api/workplace/{Id}`, 상태: 시작 전), "거점(회사 등) 삭제"(`DELETE /api/workplace`, 상태: 시작 전) 항목과 현재 코드(`Workplace`, `WorkplaceRepository`, `WorkplaceService`, `WorkplaceController`, `WorkplaceExceptionHandler`, `GeocodingClient`).
- Notion의 "거점 등록"은 상태가 "시작 전"이지만 **이미 구현되어 있다**(`POST /api/workplaces`, 목록 조회 `GET /api/workplaces` 포함). 이번 작업은 수정·삭제만 다룬다.

### 경로 확정

- Notion 속성값은 단수 `/api/workplace`지만, 이미 배포된 등록·목록 엔드포인트가 복수 `/api/workplaces`다. **기존 코드를 기준으로 복수형을 유지한다.**
- Notion의 삭제 항목은 `DELETE /api/workplace`로 식별자가 빠져 있다. 어떤 거점을 지울지 지정할 수 없으므로 **수정과 동일하게 `{workplaceId}`를 경로에 둔다.**

## 1. `PATCH /api/workplaces/{workplaceId}` — 거점 수정

### 인증

- 로그인 필수. `AuthConfig`의 `anyRequest()` 규칙이 그대로 적용되며 별도 `permitAll` 추가는 없다.
- 토큰이 없거나 유효하지 않으면 `401 INVALID_ACCESS_TOKEN`.

### 요청

`Content-Type: application/json`

```json
{
  "name": "새 이름",
  "roadAddress": "경기 수원시 팔달구 월드컵로 205"
}
```

| 이름 | 필수 | 타입 | 설명 |
| --- | --- | --- | --- |
| `name` | 아니오 | string | 거점 이름. 공백 제거 후 1~50자 |
| `roadAddress` | 아니오 | string | 도로명주소. 공백 제거 후 1~255자 |

### 검증·동작 규칙

1. 두 필드 모두 **선택**이다. 생략(또는 JSON `null`)하면 해당 값을 바꾸지 않는다(부분 수정).
2. 값이 들어온 경우 앞뒤 공백을 제거한다. 제거 후 빈 문자열이면 `400 INVALID_REQUEST`.
3. 둘 다 생략된 요청은 오류가 아니며, 아무것도 바꾸지 않고 현재 값을 `200`으로 돌려준다.
4. `roadAddress`가 **현재 값과 다를 때만** 지오코딩을 다시 호출한다. 같은 주소를 그대로 보내면 외부 호출을 하지 않는다.
5. 좌표(`lat`/`lng`)는 클라이언트가 보낼 수 없다. 항상 서버가 주소로부터 구한다(등록과 동일).

### 응답

`200 OK` — 등록 응답과 동일한 형태.

```json
{
  "id": "거점 UUID",
  "name": "새 이름",
  "roadAddress": "경기 수원시 팔달구 월드컵로 205",
  "lat": 37.279609852101984,
  "lng": 127.04339808904444
}
```

### 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 공백만 있는 값, 길이 초과, JSON 형식 오류, `workplaceId`가 UUID가 아님 |
| 400 | `ADDRESS_NOT_GEOCODABLE` | 새 주소의 좌표를 찾지 못함 |
| 401 | `INVALID_ACCESS_TOKEN` | 토큰 없음/무효 |
| 404 | `WORKPLACE_NOT_FOUND` | 거점이 없거나 **호출자의 소유가 아님** |
| 502 | `GEOCODING_UNAVAILABLE` | 지오코딩 제공자 장애 |

## 2. `DELETE /api/workplaces/{workplaceId}` — 거점 삭제

### 인증

- 로그인 필수. 수정과 동일.

### 요청

- 본문 없음. 경로의 `workplaceId`만 사용한다.

### 응답

`204 No Content` — 본문 없음.

### 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | `workplaceId`가 UUID가 아님 |
| 401 | `INVALID_ACCESS_TOKEN` | 토큰 없음/무효 |
| 404 | `WORKPLACE_NOT_FOUND` | 거점이 없거나 호출자의 소유가 아님 |

- 즐겨찾기 삭제(`DELETE /api/me/favorites/{Id}`)는 미등록이어도 `204`를 주는 멱등 방식이지만, **거점 삭제는 `404`를 준다.** 거점은 사용자가 직접 만든 이름 있는 자원이라 "없는 것을 지웠다"를 조용히 성공으로 처리하면 프런트가 잘못된 id를 쓰고 있다는 사실을 알 수 없다.

## 3. 소유권 처리 (결정 — Notion에 없어 이번 명세에서 정함)

- 조회는 항상 `id + 소유자` 조건으로 한 번에 한다. 먼저 `id`로 찾고 뒤에 소유자를 비교하는 2단계 방식은 쓰지 않는다.
- 남의 거점에 대해 `403`이 아니라 **`404`를 반환한다.** `403`은 "그 id의 거점이 존재한다"는 사실을 알려주므로, 존재 여부 자체를 감춘다.

## 4. 범위 밖

- 거점 단건 조회(`GET /api/workplaces/{workplaceId}`)는 Notion에 항목이 없어 만들지 않는다. 목록 조회로 충분하다.
- 거점 개수 상한, 이름 중복 금지 같은 제약은 등록 시점에도 없으므로 이번에도 두지 않는다.
