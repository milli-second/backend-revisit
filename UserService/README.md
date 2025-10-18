# UserService

사용자 인증 및 관리 서비스

## 📋 주요 기능

- 회원가입 / 로그인 / 로그아웃
- JWT 기반 인증
- 프로필 관리
- 비밀번호 변경
- 계정 비활성화

## 🛠️ 기술 스택

- **Java 21** + **Spring Boot 3.2.0**
- **PostgreSQL** - 사용자 데이터 저장
- **Redis** (DB 0) - 세션 관리
- **Kafka** - 이벤트 발행 (user_signup, user_login, user_logout 등)
- **JWT** - 인증 토큰

## 🚀 빠른 시작

### 1. 로컬 실행

⚠️ **필수**: 테스트/실행 전에 **Kafka, Redis, PostgreSQL**이 실행 중이어야 합니다.

```bash
docker-compose -f ../build-docker-compose.yml up -d
```

### 2. API 테스트

```bash
# 회원가입
curl -X POST http://localhost:8081/signup \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "testuser",
    "password": "password123",
    "category": "STUDENT"
  }'

# 로그인
curl -X POST http://localhost:8081/login \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "testuser",
    "password": "password123"
  }'
```

## 📡 API 엔드포인트

| 메서드 | 경로 | 설명 | 인증 필요 |
|--------|------|------|----------|
| POST | `/signup` | 회원가입 | ❌ |
| POST | `/login` | 로그인 | ❌ |
| POST | `/logout` | 로그아웃 | ✅ |
| GET | `/users/{id}` | 프로필 조회 | ✅ |
| PUT | `/users/{id}` | 프로필 수정 | ✅ |
| PUT | `/users/{id}/password` | 비밀번호 변경 | ✅ |
| DELETE | `/users/{id}` | 계정 비활성화 | ✅ |

## 🏗️ 아키텍처

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ HTTP Request
       ▼
┌──────────────────────────┐
│  UserController          │
│  - 요청 검증               │
│  - JWT 인증               │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  UserService             │
│  - 비즈니스 로직            │
│  - 이벤트 발행              │
└──┬───────────────────┬───┘
   │                   │
   ▼                   ▼
┌─────────┐      ┌──────────┐
│ Redis   │      │ Kafka    │
│ (세션)    │     │ (이벤트)   │
└─────────┘      └──────────┘
   │
   ▼
┌──────────────────┐
│  PostgreSQL      │
│  (사용자 데이터)     │
└──────────────────┘
```

### 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `default` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `SPRING_DATA_REDIS_PASSWORD` | Redis 비밀번호 | - |
| `SPRING_DATASOURCE_URL` | DB URL | `jdbc:postgresql://localhost:5432/userservice` |
| `JWT_SECRET_KEY` | JWT 시크릿 키 | - |

## 🧪 테스트

```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests UserServiceTest
```

### ⚠️ 테스트 실행 전 체크리스트

1. ✅ PostgreSQL 실행 중 (포트 5432)
2. ✅ Redis 실행 중 (포트 6379)
3. ✅ Kafka 실행 중 (포트 9092)


## 🔐 보안

### JWT 토큰

- **발급**: 로그인 성공 시 자동 발급
- **유효 기간**: 24시간
- **저장 위치**: 클라이언트 (LocalStorage 권장)
- **사용 방법**:
  ```
  Authorization: Bearer <token>
  ```

### Redis 세션

- **DB Index**: 0 (UserService 전용)
- **TTL**: 24시간
- **저장 내용**: 사용자 ID, 로그인 시간

## 📊 Kafka 이벤트

### 발행하는 이벤트

| 토픽 | 이벤트 | 설명 |
|------|--------|------|
| `user_signup` | 회원가입 | 신규 사용자 등록 시 |
| `user_login` | 로그인 | 로그인 성공 시 |
| `user_logout` | 로그아웃 | 로그아웃 시 |
| `user_password_changed` | 비밀번호 변경 | 비밀번호 변경 시 |
| `user_deactivated` | 계정 비활성화 | 계정 삭제 시 |


## 🛎️ [배포 안내 문서 ](../k8s/DEPLOY.md)

## 📝 버전

- **v1.0.0** (2025-10-18)
  - ✅ 기본 인증 기능
  - ✅ Redis 세션 관리
  - ✅ Kafka 이벤트 발행
  - ✅ Kubernetes 배포 지원
