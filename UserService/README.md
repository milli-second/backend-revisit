# UserService

## 1. 개요 (Overview)

**UserService**는 Screen Time AI 프로젝트의 사용자 계정, 인증, 권한 부여를 총괄하는 마이크로서비스입니다.

### 1.1. 주요 기능

-   **사용자 관리**: 사용자 계정의 생성(회원가입), 조회, 수정, 비활성화(탈퇴) 기능을 제공합니다.
-   **인증**: 사용자 로그인 요청을 처리하고, 인증 성공 시 JWT(JSON Web Token)를 발급합니다.
-   **프로필 관리**: 사용자의 닉네임, 프로필 이미지 등 개인 정보를 관리합니다.
-   **이벤트 발행**: 사용자 관련 주요 이벤트(예: 회원가입, 로그인, 탈퇴)를 Kafka를 통해 발행하여 다른 서비스와 비동기적으로 통신합니다.

### 1.2. 아키텍처 내에서의 역할

API Gateway를 통해 외부 요청을 받으며, 인증이 필요한 모든 마이크로서비스의 첫 관문 역할을 합니다. 사용자 데이터는 자체 PostgreSQL 데이터베이스에 저장하며, 주요 상태 변경은 Kafka 토픽으로 발행하여 `UsageService`나 `InsightService` 등 다른 서비스들이 구독하여 처리할 수 있도록 합니다.

## 2. 시작하기 (Getting Started)

### 2.1. 사전 요구사항

-   Java 21
-   Gradle 8.5+
-   Docker & Docker Compose
-   PostgreSQL, Redis, Kafka (로컬 실행 시 `infra/docker-compose.yml` 사용 권장)

### 2.2. 설치 및 빌드

```bash
# 1. 프로젝트 클론
git clone https://github.com/your-repo/screen-time-ai.git
cd screen-time-ai/UserService

# 2. Gradle을 사용하여 빌드
./gradlew build
```

### 2.3. 로컬 환경에서 실행하기

1.  **인프라 실행**: 프로젝트 루트의 `infra` 디렉토리에서 Docker Compose를 사용하여 데이터베이스와 메시지 큐를 실행합니다.

    ```bash
    # /infra 디렉토리로 이동
    cd ../infra

    # Docker Compose 실행
    docker-compose up -d
    ```

2.  **애플리케이션 실행**: `UserService`를 직접 실행하거나 IDE를 통해 실행합니다. 실행 전 `application-local.yml` 또는 환경 변수를 통해 DB 접속 정보 등을 설정해야 합니다.

    ```bash
    # UserService 디렉토리에서 실행
    ./gradlew bootRun
    ```

## 3. API 명세 (API Specification)

이 서비스는 `springdoc-openapi`를 사용하므로, 서비스 실행 후 아래 URL에서 Swagger UI를 통해 모든 API를 테스트하고 문서를 확인할 수 있습니다.

-   **Swagger UI**: `http://localhost:8081/swagger-ui.html`

### 3.1. 주요 Endpoints

| HTTP Method | Path                  | 설명                     | 인증 필요 |
| :---------- | :-------------------- | :----------------------- | :-------- |
| `POST`      | `/users/signup`       | 사용자 회원가입          | No        |
| `POST`      | `/users/login`        | 로그인 및 JWT 발급       | No        |
| `POST`      | `/users/logout`       | 로그아웃 (토큰 무효화)   | Yes       |
| `PUT`       | `/users/nickname`     | 닉네임 변경              | Yes       |
| `PUT`       | `/users/password`     | 비밀번호 변경            | Yes       |
| `PUT`       | `/users/profile-image`| 프로필 이미지 변경       | Yes       |
| `DELETE`    | `/users/me`           | 회원 탈퇴 (계정 비활성화)| Yes       |

### 3.2. 요청 예시

**회원가입**

```http
POST http://localhost:8081/users/signup
Content-Type: application/json

{
  "nickname": "newuser123",
  "password": "password123!",
  "profileImageNumber": 1
}
```

**로그인**

```http
POST http://localhost:8081/users/login
Content-Type: application/json

{
  "nickname": "newuser123",
  "password": "password123!"
}
```

### 3.3. 요청 제한 (Rate Limiting)

서비스 안정성과 보안을 위해 일부 엔드포인트에는 `Bucket4j` 라이브러리를 사용한 요청 제한 정책이 적용됩니다. 이는 무차별 대입 공격(Brute-force attack)과 같은 비정상적인 트래픽으로부터 서비스를 보호하기 위함입니다.

-   **대상 API**: `POST /users/login`
-   **제한 정책 (예시)**: IP 주소당 1분에 20회까지 요청 가능
-   **초과 시 응답**: `429 Too Many Requests` HTTP 상태 코드와 함께 에러 메시지를 반환합니다.

## 4. 아키텍처 (Architecture)

### 4.1. 기술 스택

-   **Framework**: Spring Boot 3.2.0, Spring Security, Spring Data JPA
-   **Language**: Java 21
-   **Database**: PostgreSQL (Primary), Redis (Caching/Rate Limiting)
-   **Messaging**: Kafka
-   **API Docs**: SpringDoc OpenAPI (Swagger)
-   **Service-to-Service**: OpenFeign
-   **Authentication**: JWT (jjwt library)

### 4.2. 프로젝트 구조

-   `screentimeai/application`: `UserService` 등 핵심 비즈니스 로직을 담는 서비스 클래스 위치.
-   `screentimeai/domain`: `User` 엔티티, 리포지토리 인터페이스, 도메인 이벤트 클래스 위치.
-   `screentimeai/infra`: 외부 시스템(DB, Kafka)과의 연동을 담당하는 구현체 위치.
-   `screentimeai/config`: Spring Security, JWT, CORS 등 설정 클래스 위치.

### 4.3. 도메인 주도 설계 (Domain-Driven Design)

이 서비스는 도메인 모델의 복잡성을 관리하고 비즈니스 로직을 명확하게 표현하기 위해 DDD의 일부 개념을 도입했습니다.

-   **Aggregate Root (집계 루트)**
    -   `User` 클래스는 사용자 도메인의 Aggregate Root 역할을 합니다.
    -   이는 사용자 정보와 관련된 모든 비즈니스 규칙(일관성 경계)을 `User` 엔티티 내에 캡슐화하는 것을 의미합니다. 예를 들어, 닉네임 변경, 비밀번호 검증, 계정 비활성화와 같은 핵심 로직은 `User` 객체 스스로가 책임지고 수행합니다.

-   **Domain Events (도메인 이벤트)**
    -   Aggregate의 상태 변경이 완료된 후, 관련된 부가적인 처리를 비동기적으로 수행하기 위해 도메인 이벤트를 발행합니다.
    -   `User` 엔티티 내에서 특정 동작이 수행되면 이벤트가 등록(`domainEvents.add(...)`)되고, `userRepository.save()`가 호출되는 시점에 Spring Data JPA에 의해 이벤트가 발행(publish)되어 Kafka로 전송됩니다.
    -   **주요 도메인 이벤트 목록**:
        -   `SignedUp`: 회원가입 시 발생. 신규 사용자 환영 메시지 전송 등에 사용될 수 있습니다.
        -   `LoggedIn`: 로그인 성공 시 발생. 마지막 로그인 시간 기록, 접속 통계 집계 등에 사용됩니다.
        -   `LoggedOut`: 로그아웃 시 발생.
        -   `NicknameChanged`: 닉네임 변경 시 발생.
        -   `PasswordChanged`: 비밀번호 변경 시 발생.
        -   `ProfileImageChanged`: 프로필 이미지 변경 시 발생.
        -   `AccountDeactivated`: 회원 탈퇴(비활성화) 시 발생. 관련 데이터 정리 작업에 사용될 수 있습니다.

## 5. 데이터베이스 (Database)

### 5.1. `User_table`

| Column               | Type          | 설명                                   |
| :------------------- | :------------ | :------------------------------------- |
| `id` (PK)            | `BIGINT`      | 내부 식별자 (Auto Increment)           |
| `uuid` (Unique)      | `VARCHAR(255)`| 외부 참조용 글로벌 고유 ID (UUID)      |
| `nickname` (Unique)  | `VARCHAR(255)`| 사용자 닉네임                          |
| `password`           | `VARCHAR(255)`| 암호화된 비밀번호                      |
| `active`             | `BOOLEAN`     | 계정 활성/비활성(탈퇴) 여부            |
| `profile_image_number`| `INTEGER`     | 프로필 이미지 번호 (1~8)               |
| `category`           | `VARCHAR(255)`| 사용자 카테고리 (초기값: null)         |
| `created_at`         | `TIMESTAMP`   | 생성 일시                              |
| `updated_at`         | `TIMESTAMP`   | 마지막 수정 일시                       |

## 6. 배포 (Deployment)

### 6.1. Docker 이미지 빌드

`UserService` 디렉토리의 `Dockerfile`을 사용하여 이미지를 빌드합니다.

```bash
docker build -t your-registry/userservice:latest .
```

### 6.2. Kubernetes 배포

`kubernetes` 디렉토리의 `deployment.yaml`과 `service.yaml` 파일을 사용하여 배포합니다. 배포 전, `ConfigMap`이나 `Secret`을 통해 아래 환경 변수들을 설정해야 합니다.

-   `SPRING_PROFILES_ACTIVE`: `prod`
-   `SPRING_DATASOURCE_URL`: PostgreSQL 접속 주소
-   `SPRING_DATASOURCE_USERNAME`: DB 사용자명
-   `SPRING_DATASOURCE_PASSWORD`: DB 비밀번호
-   `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka 접속 주소
-   `JWT_SECRET`: JWT 서명에 사용할 시크릿 키

## 7. 모니터링 및 로깅

-   **Health Check**: `/actuator/health`
-   **Application Info**: `/actuator/info`
-   **Metrics**: `/actuator/prometheus` (Prometheus 연동 시)

로그는 표준 출력(stdout)으로 전송되며, Kubernetes 환경에서는 Fluentd나 Logstash 같은 로그 수집 에이전트를 통해 중앙에서 관리됩니다.