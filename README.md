# jb-backend

통근 시간과 주거 선호도를 기준으로 자취방을 추천하는 서비스의 백엔드.

사용자가 회사 위치·이동수단·희망 통근시간과 채광/치안/소음/인프라 가중치, 예산·건물 조건을 등록하면
조건에 맞는 매물을 점수화해 추천한다.

## 기술 스택

| 카테고리 | 설명 |
| --- | --- |
| 언어/런타임 | Java 25 |
| 프레임워크 | Spring Boot 4.1.1 |
| DB | MySQL 8.4 + Flyway |
| 문서화 | springdoc-openapi 3 (Swagger UI) |
| 배포 | GitHub Actions → GHCR → EC2 (Docker Compose) |

## 시작하기

사전 요구사항: JDK 25, Docker.

```bash
cp .env.example .env      # 값 채우기 (구글 클라이언트 ID, JWT 시크릿 등)
docker compose up -d      # MySQL 기동 (호스트 3307)
./gradlew bootRun
```

앱은 `http://localhost:8080`, API 문서는 `http://localhost:8080/swagger-ui/index.html`.

Flyway가 기동 시 마이그레이션을 적용하므로 별도 스키마 작업은 없다.

### 환경변수

`.env.example`에 전체 목록과 설명이 있다. 기본값이 없는 값들이라 누락되면 기동에 실패한다.

| 키 | 설명 |
| --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 데이터소스 |
| `GOOGLE_CLIENT_ID` | 구글 로그인 ID 토큰 검증용 |
| `SWAGGER_GOOGLE_LOGIN_ENABLED` | 로컬·개발 Swagger 구글 로그인 버튼 활성화. 기본 `false`, 운영에서는 끄기 |
| `AUTH_ACCESS_TOKEN_SECRET` / `AUTH_REFRESH_TOKEN_SECRET` | JWT 서명 키. 각각 32바이트 이상, 서로 다른 값 |
| `AUTH_COOKIE_SECURE` | refresh 토큰 쿠키의 Secure 속성. HTTP 환경에서만 `false` |
| `AUTH_ALLOWED_ORIGINS` | 인증 요청을 허용할 프론트엔드 오리진 (쉼표 구분) |
| `VWORLD_API_KEY` | 거점·매물의 도로명주소를 WGS84 좌표로 변환하는 VWorld API 키 |

### Swagger에서 구글 로그인

로컬·개발 환경의 `.env`에서 `SWAGGER_GOOGLE_LOGIN_ENABLED=true`로 설정하고 앱을 재시작한다.
Google Console의 웹 OAuth Client ID에 **Authorized JavaScript origins**로
`http://localhost`와 `http://localhost:8080`을 추가한다. 개발 서버는 실제 HTTPS Origin을 추가한다.
Swagger와 서버는 같은 Origin을 사용하므로 `AUTH_ALLOWED_ORIGINS`에 Swagger 주소를 추가할 필요는 없다.
로컬 HTTP에서는 `AUTH_COOKIE_SECURE=false`, HTTPS 개발 환경에서는 `true`를 사용한다.

기존 `/swagger-ui/index.html`에서 구글 로그인 버튼을 누르면 서버 JWT가 자동으로 Authorize에 등록된다.
토큰은 메모리에만 유지하며 새로고침하거나 15분이 지나면 다시 로그인한다. 자동 재발급은 하지 않는다.
로그아웃 버튼은 서버 Refresh Token 세션과 Swagger 인증을 해제한다. 수동 Authorize도 계속 사용할 수 있다.
최초 로그인은 기존 정책대로 사용자 가입이 진행되며 관리자 권한을 부여하지 않는다.
기능을 끄면 기존 Swagger 화면만 제공하고 Google 스크립트를 로드하지 않는다.

구글 Client ID는 브라우저에 전달되는 공개 식별자이며 JWT 서명 키와 토큰은 소스나 로그에 남기지 않는다.
별도 Client Secret이나 OAuth 리다이렉트 콜백 API는 필요하지 않다.

## 빌드와 테스트

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootJar    # 실행 가능한 jar만
node --test src/test/js/*.test.cjs # Swagger 로그인 UI 로직 (Node.js 24, 추가 패키지 없음)
```

테스트는 Testcontainers로 MySQL 컨테이너를 띄우므로 Docker가 실행 중이어야 한다.

### 로컬 매물 seed

[dummy-seed.sql](tools/local-dev/dummy-seed.sql)은 API로 등록한 매물의 UUID·좌표·가격·거래 유형을
보존한 로컬 개발용 데이터다. 사용자·토큰 데이터는 포함하지 않으며, Flyway에서 자동 실행하지 않는다.

로컬 MySQL을 실행하고 앱을 한 번 기동해 **Flyway V6까지 적용한 뒤**, 저장소 루트에서 실행한다.
아래 명령은 로컬 `compose.yaml`의 MySQL에만 사용하며 운영 DB에서는 실행하지 않는다.

```bash
docker compose -f compose.yaml exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 --user="$MYSQL_USER" --database="$MYSQL_DATABASE"' \
  < tools/local-dev/dummy-seed.sql
```

반복 실행해도 같은 UUID는 중복 등록되지 않고 **기존 행도 덮어쓰지 않는다**.
따라서 이미 등록된 매물의 값을 수정하려는 용도로는 사용하지 않는다.
seed의 영통구 시군구 코드는 원본의 `41115`를 `41117`로 바로잡았으며, 기존 로컬 DB 행을 자동 수정하지 않는다.

나중에 API로 매물을 더 등록하면 해당 행을 SQL의 `VALUES` 목록에 추가하면 된다.
UUID는 `HEX(id)`로 조회한 값을 `X'…'` 형태로 보존하고 컬럼 순서를 맞춘다.
문자열의 작은따옴표는 `''`로 이스케이프하며, 사용자·인증 데이터를 추가하지 않는다.

## API

전체 명세는 Swagger UI에서 확인한다. 주요 엔드포인트는 다음과 같다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/auth/login/google` | 구글 ID 토큰으로 로그인, 액세스 토큰 발급 + refresh 쿠키 설정 |
| POST | `/api/auth/reissue` | refresh 쿠키로 액세스 토큰 재발급 |
| POST | `/api/logout` | refresh 세션 폐기 및 쿠키 만료 |
| POST | `/api/properties` | DB의 현재 역할이 ADMIN인 사용자만 매물 등록 가능 |

그 외 요청은 인증이 필요하다. 액세스 토큰은 `Authorization: Bearer <token>`으로 보낸다.
공개 엔드포인트를 추가할 때는 `AuthConfig`의 `securityFilterChain`에 permitAll을 함께 등록해야 한다.

매물 등록은 도로명주소로 서버가 좌표를 구한다. 보증금·월세 단위는 만원이며,
`leaseType`은 전세 `JEONSE` 또는 월세 `MONTHLY`를 필수로 지정한다.
등록 필드와 오류 응답은 [매물 등록 명세](docs/specs/property-registration.md)를 참고한다.

## 배포

`main`에 푸시하면 자동 배포된다.

```
gradle build → GHCR 이미지 push (:latest, :커밋SHA) → EC2에 compose.prod.yaml 전송 → docker compose up -d
```

PR에서는 빌드와 테스트만 실행하고 배포하지 않는다.

운영 환경의 설정값은 서버의 `.env`로 관리하며 저장소에 포함되지 않는다.
환경변수를 새로 추가할 때는 **서버 `.env`에 값을 먼저 넣은 뒤** 코드를 푸시해야 한다.
순서가 바뀌면 새 인스턴스가 빈 값으로 기동하다 실패한다.

자세한 운영 규칙은 `CLAUDE.md` 참고.

## 마이그레이션 규칙

- 이미 적용된 마이그레이션 파일은 수정하지 않는다. Flyway가 체크섬 불일치로 기동을 거부한다.
- 스키마 변경은 항상 새 `V{n}__설명.sql` 파일로 추가한다.
- `ddl-auto`가 `validate`이므로 엔티티와 스키마가 어긋나면 애플리케이션이 뜨지 않는다.
