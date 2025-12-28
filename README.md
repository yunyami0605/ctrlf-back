# ctrlf-back

멀티 모듈 Spring Boot 백엔드입니다. 각 서비스는 공통 Postgres(DB)와 Keycloak을 사용합니다.

## 구성

| 서비스            | 포트 | 설명           |
| ----------------- | ---- | -------------- |
| chat-service      | 9005 | 챗봇 서비스    |
| education-service | 9002 | 교육 서비스    |
| infra-service     | 9003 | 인프라 서비스  |
| quiz-service      | 9004 | 퀴즈 서비스    |
| api-gateway       | 8080 | API 게이트웨이 |
| Postgres          | 5432 | 데이터베이스   |
| Keycloak          | 8090 | 인증 서버      |

## 사전 준비물

- Java 17
- Docker / Docker Compose v2
- Gradle Wrapper(동봉): `./gradlew`
- AWS CLI (S3 연동 시 필요)

## 빠른 시작

### 1. 인프라(Docker) 기동

```bash
# 루트(ctrlf-back)에서 실행
docker compose up -d

# 상태 확인
docker compose ps

# 로그 확인
docker compose logs -f postgres
```

- Postgres: 사용자/비밀번호/DB = `postgres` / `postgres` / `db`
- Keycloak: `http://localhost:8090`
  - 관리자 계정: `admin` / `admin`
  - 처음 기동 시 `keycloak-realms/`의 realm이 자동 import됩니다(`--import-realm`).

### 2. 애플리케이션 실행(개별 서비스)

```bash
# 각 서비스는 별도 터미널에서 실행 권장
# AWS_PROFILE 설정이 필요한 경우 (S3 연동 등)
# infra-service 부터 켜야지 education-service 더미(시드) 데이터가 저장된다
AWS_PROFILE=sk_4th_team04 SPRING_PROFILES_ACTIVE=local,local-seed ./gradlew --no-configuration-cache :infra-service:bootRun
AWS_PROFILE=sk_4th_team04 SPRING_PROFILES_ACTIVE=local,local-seed ./gradlew --no-configuration-cache :education-service:bootRun

AWS_PROFILE=sk_4th_team04 ./gradlew :chat-service:bootRun
AWS_PROFILE=sk_4th_team04 ./gradlew :education-service:bootRun --no-configuration-cache
AWS_PROFILE=sk_4th_team04 ./gradlew :infra-service:bootRun --no-configuration-cache
AWS_PROFILE=sk_4th_team04 ./gradlew :quiz-service:bootRun
AWS_PROFILE=sk_4th_team04 ./gradlew :api-gateway:bootRun
```

### 3. 시드 데이터 포함 실행

```bash
# education-service (교육 시드 데이터 포함)
AWS_PROFILE=sk_4th_team04 SPRING_PROFILES_ACTIVE=local,local-seed ./gradlew --no-configuration-cache :education-service:bootRun

# infra-service (인프라 시드 데이터 포함)
AWS_PROFILE=sk_4th_team04 ./gradlew :infra-service:bootRun --args='--spring.profiles.active=local,local-seed'
```

- DB 연결 정보는 각 서비스의 `application.yml`에 정의되어 있습니다(호스트 기준 `localhost:5432`).
- Flyway가 서비스별 스키마로 마이그레이션을 적용합니다.
  - chat: `chat` 스키마
  - education: `education` 스키마
  - infra: `infra` 스키마
  - quiz: `quiz` 스키마

### 4. API 문서(Swagger/OpenAPI)

| 서비스    | Swagger UI                            | OpenAPI                           |
| --------- | ------------------------------------- | --------------------------------- |
| Chat      | http://localhost:9005/swagger-ui.html | http://localhost:9005/v3/api-docs |
| Education | http://localhost:9002/swagger-ui.html | http://localhost:9002/v3/api-docs |
| Infra     | http://localhost:9003/swagger-ui.html | http://localhost:9003/v3/api-docs |
| Quiz      | http://localhost:9004/swagger-ui.html | http://localhost:9004/v3/api-docs |

- springdoc 2.x 사용 중이며, 일부 환경에서는 `/swagger-ui/index.html` 경로를 사용하기도 합니다.
- 공통 OpenAPI 설정(`libs/common-utils`)에서 보안 스키마(JWT Bearer) 사용을 켜고 끌 수 있습니다(`app.api.security.enabled`).

## Keycloak 연동

### 기본 설정

| 항목     | 값                      |
| -------- | ----------------------- |
| Base URL | `http://localhost:8090` |
| Realm    | `ctrlf`                 |
| Client   | `infra-admin`           |
| Secret   | `changeme`              |

- 값은 `infra-service/src/main/resources/application.yml`에서 변경할 수 있습니다.

### 테스트 계정

| 계정      | 비밀번호 | 역할              | 용도             |
| --------- | -------- | ----------------- | ---------------- |
| user1     | 11111    | EMPLOYEE          | 일반 직원        |
| admin1    | 22222    | SYSTEM_ADMIN      | 시스템 관리자    |
| reviewer1 | 33333    | CONTENTS_REVIEWER | 콘텐츠 검토자    |
| creator1  | 44444    | VIDEO_CREATOR     | 교육 영상 제작자 |

### 토큰 발급 (curl)

```bash
curl -s -X POST 'http://localhost:8090/realms/ctrlf/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=infra-admin' \
  -d 'client_secret=changeme' \
  -d 'username=creator1' \
  -d 'password=44444' | jq -r '.access_token'
```

## 빌드/테스트/포맷

- 포매터: Eclipse(Java) 4칸 들여쓰기, 불필요 import 제거, import 정렬.
- VS Code용 워크스페이스 설정은 `.vscode/settings.json`을 참고하세요.

## 문제 해결 (Troubleshooting)

| 문제                  | 해결 방법                                                                  |
| --------------------- | -------------------------------------------------------------------------- |
| 포트 충돌             | 5432/8090/9002~9005 사용 중인 프로세스가 없는지 확인                       |
| DB 연결 실패          | `docker compose ps`로 Postgres 상태 확인, healthcheck 통과 여부 확인       |
| 마이그레이션 오류     | Flyway `validate` 오류는 스키마 불일치. 초기화 필요 시 볼륨 삭제 후 재기동 |
| Keycloak realm 미적용 | Keycloak 로그에 `--import-realm` 수행 여부 확인. 필요 시 컨테이너 재기동   |
| AWS 자격 증명 오류    | `AWS_PROFILE` 환경변수 설정 확인 또는 `~/.aws/credentials` 파일 확인       |

## DB 접속 및 관리

### DB 접속

```bash
docker exec -it platform-postgres psql -U postgres -d db
```

### 스키마 확인 명령어 (psql 내부)

```sql
\dn                           -- 스키마 목록
\dt education.*               -- education 스키마 테이블
\dt infra.*                   -- infra 스키마 테이블
\dt chat.*                    -- chat 스키마 테이블
\dt quiz.*                    -- quiz 스키마 테이블

SET search_path=education;    -- 기본 스키마 변경
\dt                           -- 현재 스키마의 테이블 목록
```

### 초기 데이터

- 초기 스키마/데이터: `postgres-init/`의 스크립트가 최초 기동 시 자동 적용됩니다.

## Keycloak 재시작

Keycloak에 문제가 발생한 경우 아래 순서로 재시작합니다.

```bash
# 1) Keycloak 관련 컨테이너 정지
docker compose stop keycloak keycloak-db

# 2) 서비스 컨테이너 삭제 (이미지/볼륨은 유지)
docker compose rm -f keycloak keycloak-db

# 3) 볼륨 삭제 (완전 초기화 필요 시)
docker volume rm ctrlf-back_kc-db-data

# 4) DB -> Keycloak 순으로 재기동
docker compose up -d keycloak-db
docker compose up -d keycloak

# 5) 기동 로그 확인
docker logs -f platform-keycloak
```

## Git 브랜치 전략

- 브랜치

  - `main`: 보호 브랜치(배포/안정). 직접 푸시 금지, PR만 병합.
  - 기능 단위 작업은 토픽 브랜치에서 시작:
    - `feature/add-education`
    - `fix/<scope>-<short-desc>`
    - `refactor/<scope>-<short-desc>`
    - `chore/<scope>-<short-desc>`
  - 릴리스 태그: `vMAJOR.MINOR.PATCH` (예: `v1.0.0`).

- PR 규칙

  - Draft → 리뷰 요청 → 승인 1+ → 상태 체크 통과(빌드/테스트/Spotless) → 스쿼시 병합 권장.
  - 작은 단위의 PR(리뷰 10~15분 내)을 지향.
  - 제목은 커밋 컨벤션(아래)과 동일 형식 사용.

- 커밋 컨벤션(Conventional Commits)
  - 형식: `type(scope): subject`
  - 주요 타입:
    - `feat`: 기능 추가
    - `fix`: 버그 수정
    - `refactor`: 리팩터링(기능 변경 없음)
    - `style`: 포맷/세미콜론 등 비기능 변경
    - `test`: 테스트 추가/수정
    - `docs`: 문서
    - `chore`: 빌드 시스템, 의존성, ci 설정, 셋업
  - 예시:
    - `feat(education): 강의 생성 API 추가`
    - `fix(chat): NPE 방지를 위한 null 체크`
    - `refactor(common-utils): OpenAPI 설정 메서드 분리`

## 코드 컨벤션

- 기본

  - Java 17, 들여쓰기 4칸(스페이스). `.editorconfig` + Spotless(Eclipse formatter) 적용.
  - 저장 시 포맷팅: VS Code 워크스페이스 설정 참고(`.vscode/settings.json`).
  - 포맷 명령: `./gradlew spotlessApply` / 검사: `./gradlew spotlessCheck`.

- 네이밍

  - 패키지: 전부 소문자, 약어 지양(예: `com.ctrlf.education.service`).
  - 클래스/인터페이스: PascalCase (예: `EducationService`).
  - 메서드/변수: camelCase (예: `createEducation`).
  - 상수: UPPER_SNAKE_CASE (예: `DEFAULT_TIMEOUT_MS`).
  - DTO/엔티티 접미사: `Request`, `Response`, `Dto`, `Entity` 등 의미 드러나게.

- 코드 스타일

  - import: 와일드카드 금지, 정렬/미사용 제거 자동화(Spotless `importOrder`, `removeUnusedImports`).
  - 의존성 주입: 생성자 주입 권장, `final` 필드 사용.
  - 로깅: `@Slf4j` 또는 명시적 로거, 민감정보 로깅 금지.
  - null 처리: 메서드 반환에 한해 `Optional` 고려, 파라미터는 명시적 검증/제약 사용.
  - 예외: 도메인별 커스텀 예외 사용, 전역 핸들러(`exception/GlobalExceptionHandler.java`)로 공통 응답.
  - 컨트롤러: `ResponseEntity<T>` 반환, 엔티티 직접 노출 금지(DTO 매핑).
  - 검증: Bean Validation(`jakarta.validation`) 애너테이션 적극 사용.

- 트랜잭션/영속성

  - 서비스 계층에서 트랜잭션 경계 설정(`@Transactional`), 읽기 전용은 `readOnly = true`.
  - 지연 로딩 주의, N+1 회피(Query 최적화/Fetch join/전용 조회 레포지토리).

- 테스트
  - JUnit 5
