# Google OAuth 로그인 명세

- 상태: 확정
- 대상 API: Google 로그인, 토큰 재발급, 로그아웃
- 작성 원칙: 이 문서의 기대 동작을 테스트와 구현의 기준으로 사용한다. 현재 구현과 다를 경우 테스트를 현재 구현에 맞추지 않는다.

| 기능 | Method | API | 인증 | 성공 응답 |
|---|---|---|---|---|
| Google 로그인 | POST | `/api/auth/login/google` | 불필요 | 200 |
| 토큰 재발급 | POST | `/api/auth/reissue` | Refresh Token 쿠키 | 200 |
| 로그아웃 | POST | `/api/logout` | Refresh Token 쿠키 사용 | 204 |

## 1. 범위와 인증 방식

- 로그인 제공자는 Google만 지원한다.
- 프론트엔드는 Google Identity Services를 통해 Google ID Token을 발급받는다.
- 프론트엔드는 발급받은 ID Token을 `POST /api/auth/login/google`에 전달한다.
- 백엔드는 Google ID Token을 검증한 후 서비스의 Access Token과 Refresh Token을 발급한다.
- 서비스 토큰은 JWT를 사용한다.
- Access Token은 응답 본문으로 전달하고, 클라이언트는 메모리에 보관한다.
- Refresh Token은 응답 본문에 포함하지 않고 HttpOnly 쿠키로 전달한다.

## 2. 사용자 식별 및 가입

- 사용자는 `(provider, provider_id)` 조합으로 식별한다.
- Google 사용자의 값은 다음과 같다.
  - `provider`: `google`
  - `provider_id`: 검증된 Google ID Token의 `sub`
- 이메일은 사용자 식별 키로 사용하지 않는다.
- 같은 `(provider, provider_id)` 사용자가 없으면 최초 로그인 시 자동으로 가입시킨다.
- 신규 사용자의 `email`은 Google ID Token의 `email`로 저장한다.
- 신규 사용자의 기본 권한은 `USER`이다.
- 동시에 같은 Google 계정으로 최초 로그인을 시도해도 사용자는 하나만 생성되어야 한다.
- 같은 `(provider, provider_id)` 사용자가 존재하면 신규 사용자를 생성하지 않고 기존 사용자로 로그인한다.
- 기존 사용자의 Google 이메일이 변경되었다면 재로그인 시 최신 이메일로 갱신한다.
- Google ID Token에 `email`이 없으면 로그인에 실패한다.
- `email_verified`가 `false`이거나 claim이 없어도 그것만을 이유로 로그인을 거절하지 않는다.
- 이메일 또는 Google Workspace 도메인에 따른 가입 제한은 없다.

## 3. 닉네임 및 가입 완료 상태

- Google이 제공하는 이름은 서비스 닉네임으로 사용하지 않는다.
- 신규 사용자의 `nickname`은 `null`로 저장한다.
- 이에 맞춰 `users.nickname` 컬럼과 엔티티는 nullable로 변경해야 한다.
- 사용자는 로그인 후 별도의 닉네임 설정 기능을 통해 닉네임을 직접 지정한다.
- `nickname == null`이면 `profileCompleted`는 `false`, 닉네임이 있으면 `true`이다.
- Google 재로그인은 사용자가 지정한 닉네임을 변경하지 않는다.
- 닉네임이 설정되지 않은 사용자에게도 Access Token과 Refresh Token을 발급한다.
- 닉네임 설정 전에는 인증 및 닉네임 설정 API만 호출할 수 있다.
- 그 밖의 인증 필요 API를 호출하면 `403 PROFILE_INCOMPLETE`를 반환한다.
- 닉네임 설정 API의 상세 계약은 별도 명세에서 정의한다.

## 4. 토큰 정책

### 4.1 Access Token

- 형식: JWT
- 만료 시간: 발급 시점부터 15분
- 전달 위치: 로그인 및 재발급 성공 응답 본문
- 사용 방식: `Authorization: Bearer {accessToken}` 요청 헤더
- 필수 claim:
  - `sub`: 서비스 사용자 UUID의 문자열 표현
  - `role`: 사용자 권한
  - `iat`: 발급 시각
  - `exp`: 만료 시각
  - `token_type`: `access`
- 로그아웃해도 이미 발급된 Access Token을 별도 차단 목록에 등록하지 않는다.
- 로그아웃 전에 발급된 Access Token은 남은 만료 시간 동안 유효하다.

### 4.2 Refresh Token

- 형식: JWT
- 만료 시간: 발급 시점부터 14일
- 필수 claim:
  - `sub`: 서비스 사용자 UUID의 문자열 표현
  - `jti`: 토큰별 고유 식별자
  - `iat`: 발급 시각
  - `exp`: 만료 시각
  - `token_type`: `refresh`
- 쿠키 이름: `refresh_token`
- 쿠키 속성:
  - `HttpOnly=true`
  - `Secure=true` (운영 환경)
  - `SameSite=Lax`
  - `Path=/`
  - `Max-Age=1209600`
- 로컬 HTTP 개발 환경에서만 `Secure=false`를 허용한다.
- 서버에는 Refresh Token 원문을 저장하지 않고 SHA-256 해시와 세션 식별 정보만 저장한다.
- 로그인할 때마다 독립된 Refresh Token 세션을 생성하여 여러 기기의 동시 로그인을 허용한다.
- 동시 로그인 세션 수에는 제한을 두지 않는다.
- 재발급이 성공할 때마다 Refresh Token을 회전한다.
- 회전된 이전 Refresh Token은 즉시 폐기하며 다시 사용할 수 없다.
- 폐기된 Refresh Token의 재사용이 감지되면 해당 로그인 세션을 폐기한다.

### 4.3 서명 및 비밀정보

- Access Token과 Refresh Token은 서로 다른 서명 키를 사용한다.
- 서명 알고리즘은 `HS256`을 사용한다.
- 서명 키, Google Client ID와 토큰 원문은 소스 코드 또는 로그에 남기지 않고 환경 설정으로 주입한다.

## 5. API 계약

모든 요청과 성공 응답의 JSON Content-Type은 `application/json`이다. 오류 응답은 6절의 `application/problem+json` 형식을 사용한다.

### 5.1 Google 로그인

`POST /api/auth/login/google`

인증: 불필요

요청:

```json
{
  "idToken": "google-id-token"
}
```

검증 규칙:

- `idToken`은 필수이며 빈 문자열일 수 없다.
- Google 공개 키로 서명을 검증한다.
- `iss`가 Google의 허용된 발급자인지 검증한다.
- `aud`가 서비스에 설정된 Google Client ID와 일치하는지 검증한다.
- `exp`가 지나지 않았는지 검증한다.
- `sub`와 `email`이 존재하는지 검증한다.

성공 응답: `200 OK`

헤더:

```text
Set-Cookie: refresh_token={token}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1209600
```

본문:

```json
{
  "accessToken": "service-access-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "isNewUser": true,
  "user": {
    "id": "a3e6c764-d029-4f15-a7e4-8c5d1f91c8e8",
    "email": "user@example.com",
    "nickname": null,
    "profileCompleted": false
  }
}
```

응답 규칙:

- `expiresIn`의 단위는 초이다.
- 최초 가입이면 `isNewUser=true`, 기존 사용자 로그인이면 `false`이다.
- `nickname`이 설정되지 않았으면 JSON `null`이다.
- 같은 Google 사용자로 다시 로그인해도 사용자 ID는 유지된다.
- 로그인마다 새로운 Access Token과 Refresh Token을 발급한다.

실패:

- 요청 형식 오류: `400 INVALID_REQUEST`
- Google ID Token 검증 실패: `401 INVALID_GOOGLE_TOKEN`
- Google 공개 키 조회 등 외부 연동 실패: `502 GOOGLE_AUTH_UNAVAILABLE`

### 5.2 토큰 재발급

`POST /api/auth/reissue`

인증: `refresh_token` 쿠키 필수, Access Token 불필요

요청 본문: 없음

성공 응답: `200 OK`

헤더에는 회전된 새 Refresh Token 쿠키를 포함한다.

```text
Set-Cookie: refresh_token={new-token}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1209600
```

본문:

```json
{
  "accessToken": "new-service-access-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

성공 시 기존 Refresh Token은 즉시 폐기한다. 새 Refresh Token은 기존 토큰의 최초 로그인 세션에 속하며 만료 시간은 회전 시점부터 다시 14일로 계산한다.

실패:

- 쿠키 없음, 잘못된 서명, 만료, 잘못된 토큰 종류, 서버에 없는 토큰, 이미 폐기된 토큰은 모두 `401 INVALID_REFRESH_TOKEN`으로 동일하게 응답한다.
- 실패 원인의 세부 차이는 외부 응답에 노출하지 않는다.
- 재발급 실패 시 Access Token과 새 Refresh Token을 발급하지 않는다.

### 5.3 로그아웃

`POST /api/logout`

인증: `refresh_token` 쿠키 사용, Access Token 불필요

요청 본문: 없음

성공 응답: `204 No Content`

헤더:

```text
Set-Cookie: refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
```

처리 규칙:

- 요청의 Refresh Token에 해당하는 현재 로그인 세션만 폐기한다.
- 다른 기기의 로그인 세션은 유지한다.
- Refresh Token이 없거나 이미 유효하지 않아도 `204 No Content`를 반환한다.
- 로그아웃은 멱등성을 보장한다.
- 응답 본문은 없다.

## 6. 오류 응답 계약

오류는 RFC 9457 Problem Details 구조를 사용한다.

Content-Type:

```text
application/problem+json
```

본문:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "인증에 실패했습니다.",
  "instance": "/api/auth/login/google",
  "code": "INVALID_GOOGLE_TOKEN",
  "timestamp": "2026-09-14T12:34:56+09:00"
}
```

공통 규칙:

- `code`는 클라이언트가 분기 처리하는 안정적인 식별자이다.
- `detail`은 사용자 화면에 그대로 노출하는 문구가 아니라 개발 및 운영 확인용 설명이다.
- 내부 예외명, 스택 트레이스, 서명 키, 토큰 원문, Google 응답 원문은 포함하지 않는다.
- 예상하지 못한 서버 오류는 `500 INTERNAL_SERVER_ERROR`로 응답한다.

| HTTP 상태 | code | 사용 조건 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 필드 누락 또는 잘못된 요청 형식 |
| 401 | `INVALID_GOOGLE_TOKEN` | Google ID Token 검증 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh Token이 없거나 유효하지 않음 |
| 401 | `INVALID_ACCESS_TOKEN` | Access Token이 없거나 유효하지 않음 |
| 403 | `PROFILE_INCOMPLETE` | 닉네임 설정 전 허용되지 않은 API 호출 |
| 502 | `GOOGLE_AUTH_UNAVAILABLE` | Google 공개 키 등 외부 인증 인프라 이용 불가 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류 |

## 7. 보안 요구사항

- Google ID Token의 서명, `iss`, `aud`, `exp`를 모두 검증한 뒤 claim을 사용한다.
- Access Token과 Refresh Token의 `token_type`을 검증하여 서로 대신 사용할 수 없게 한다.
- 인증 토큰을 URL 경로 또는 쿼리 문자열에 넣지 않는다.
- 인증 토큰이나 비밀정보를 로그에 기록하지 않는다.
- 로그인, 재발급, 로그아웃 요청은 허용된 Origin인지 검증한다.
- Refresh Token 쿠키를 사용하는 재발급과 로그아웃은 `POST`만 허용한다.
- 브라우저 클라이언트는 로그인, 재발급, 로그아웃 요청에 쿠키를 주고받도록 credentials 옵션을 포함한다.
- 서버의 CORS 설정은 허용된 프론트엔드 Origin과 credential 요청만 명시적으로 허용하며 와일드카드 Origin을 사용하지 않는다.
- 사용자 생성과 Refresh Token 세션 생성은 하나의 트랜잭션으로 처리한다.
- `(provider, provider_id)` 유일 제약을 DB에서 유지한다.
- 외부 Google 연동 실패 시 사용자를 생성하거나 토큰을 발급하지 않는다.

## 8. 시나리오별 기대 결과

### 8.1 신규 사용자 로그인

- 유효한 Google ID Token이고 같은 `(google, sub)` 사용자가 없다.
- 사용자 한 명을 생성하고 `isNewUser=true`로 응답한다.
- `nickname=null`, `profileCompleted=false`, `role=USER`이다.
- Access Token과 Refresh Token을 발급한다.

### 8.2 기존 사용자 로그인

- 유효한 Google ID Token이고 같은 `(google, sub)` 사용자가 있다.
- 사용자를 새로 만들지 않고 `isNewUser=false`로 응답한다.
- Google 이메일이 달라졌으면 저장된 이메일만 갱신한다.
- 사용자가 지정한 닉네임과 권한은 변경하지 않는다.

### 8.3 최초 로그인 동시 요청

- 같은 Google 사용자의 유효한 ID Token으로 요청이 동시에 들어온다.
- `(provider, provider_id)`가 같은 사용자는 최종적으로 한 명만 존재한다.
- 두 요청 모두 같은 서비스 사용자 ID로 정상 로그인되거나, 경합을 감지한 요청이 생성된 사용자를 다시 조회하여 정상 로그인한다.
- DB 유일 제약 예외를 `500`으로 노출하지 않는다.

### 8.4 잘못된 Google ID Token

- 사용자 데이터를 생성하거나 수정하지 않는다.
- 서비스 Access Token과 Refresh Token을 발급하지 않는다.
- `401 INVALID_GOOGLE_TOKEN`을 반환한다.

### 8.5 재발급과 회전

- 유효한 Refresh Token이면 새 Access Token과 새 Refresh Token을 발급한다.
- 사용한 이전 Refresh Token은 즉시 폐기한다.
- 이전 Refresh Token을 다시 사용하면 `401 INVALID_REFRESH_TOKEN`을 반환한다.

### 8.6 로그아웃

- 현재 Refresh Token 세션을 폐기하고 쿠키를 삭제한다.
- 같은 Refresh Token으로 이후 재발급할 수 없다.
- 반복 로그아웃 요청도 `204`를 반환한다.

## 9. 범위 밖

- Google 인증 화면 자체의 UI 구현
- 닉네임 설정 API의 상세 계약
- Google 이외 제공자 로그인
- 이메일/비밀번호 로그인
- 이메일 기반 계정 병합
- 특정 이메일 또는 회사 도메인 제한
- 모든 기기에서 로그아웃
- 회원 탈퇴 및 Google 계정 연결 해제

## 10. 테스트 작성 원칙

- 구현 전에 이 문서의 정상·실패·경계 시나리오에 대한 테스트를 작성한다.
- 구현이 없어서 실패하는 테스트와 테스트 설정 오류를 구분한다.
- 구현 결과가 테스트와 다르다는 이유만으로 기대값을 변경하지 않는다.
- 명세 변경이 필요하면 변경 이유와 제품 결정을 먼저 기록한 뒤 테스트를 수정한다.
- Google 외부 통신은 테스트 대역으로 통제하고, 토큰 검증 결과를 결정적으로 재현한다.
- 토큰 원문 자체가 아니라 claim, 만료 시간 범위, 쿠키 속성 및 폐기 여부를 검증한다.
