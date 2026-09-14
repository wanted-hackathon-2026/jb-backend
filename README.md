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
| `AUTH_ACCESS_TOKEN_SECRET` / `AUTH_REFRESH_TOKEN_SECRET` | JWT 서명 키. 각각 32바이트 이상, 서로 다른 값 |
| `AUTH_COOKIE_SECURE` | refresh 토큰 쿠키의 Secure 속성. HTTP 환경에서만 `false` |
| `AUTH_ALLOWED_ORIGINS` | 인증 요청을 허용할 프론트엔드 오리진 (쉼표 구분) |

## 빌드와 테스트

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootJar    # 실행 가능한 jar만
```

테스트는 Testcontainers로 MySQL 컨테이너를 띄우므로 Docker가 실행 중이어야 한다.

## API

전체 명세는 Swagger UI에서 확인한다. 인증 관련 엔드포인트는 다음과 같다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/api/auth/login/google` | 구글 ID 토큰으로 로그인, 액세스 토큰 발급 + refresh 쿠키 설정 |
| POST | `/api/auth/reissue` | refresh 쿠키로 액세스 토큰 재발급 |
| POST | `/api/logout` | refresh 세션 폐기 및 쿠키 만료 |

그 외 요청은 인증이 필요하다. 액세스 토큰은 `Authorization: Bearer <token>`으로 보낸다.
공개 엔드포인트를 추가할 때는 `AuthConfig`의 `securityFilterChain`에 permitAll을 함께 등록해야 한다.

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
