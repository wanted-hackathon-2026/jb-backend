# jb-backend

Spring Boot 4.1.1 / Java 25 / MySQL 8.4 / Flyway. GHCR 이미지를 EC2에 docker compose로 배포.

## 명령어

- `./gradlew build` — 컴파일 + 테스트. 테스트가 Testcontainers를 써서 로컬 Docker 필요.
- `docker compose up -d` — 로컬 MySQL만 기동 (호스트 3307 → 컨테이너 3306). 앱은 IDE에서 실행.

## 설정

- 로컬 값은 `.env` (gitignore). 키 목록은 `.env.example`.
- `application.yaml`은 `${VAR}` 참조만 두고 기본값을 넣지 않음 — 값이 없으면 기동이 실패해야 정상.

## 배포 (main 푸시 시 자동, ~2분 30초)

- `deploy.yml`: gradle build → GHCR push(`:latest` + `:커밋SHA`) → `compose.prod.yaml` scp → `docker compose up -d --pull always`
- `compose.prod.yaml`은 **repo가 단일 진실 공급원**. 서버 파일을 고치면 다음 배포에 덮어써짐.
- 서버 `.env`는 scp 대상이 아니고 git에도 없음. 값 추가는 서버에서 직접.
- **환경변수 추가 순서**: 서버 `.env` → `compose.prod.yaml` 전달 → `application.yaml` 참조 → `.env.example` → push. 뒤집으면 빈 값으로 기동해 크래시 루프.
- PR은 `ci.yml`(빌드만) 실행, 배포 없음.

## 주의

- Flyway는 앱 기동 시 실행. **적용된 마이그레이션 파일 수정 금지** — 체크섬 불일치로 기동 거부. 항상 새 `V{n}__` 추가.
- `ddl-auto: validate`라 엔티티/스키마 불일치 시 기동 실패. 헬스체크 UP = 스키마 일치 보장.
- springdoc은 **3.x** (Spring Boot 4 대응). 2.x는 Boot 3용이라 안 붙음.
- Security가 `anyRequest().authenticated()`라 새 공개 엔드포인트는 `AuthConfig`에 permitAll 추가 필요.
- 배포 서버는 HTTP라 `AUTH_COOKIE_SECURE=false`. 프론트가 HTTPS 프록시를 붙이면 true로 전환.
