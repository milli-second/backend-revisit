# Backend Services

<p align="center">
  <img src="https://github.com/user-attachments/assets/b7ff44ef-3c05-4e05-9319-3c8af74f672d" alt="스크린샷 2025-10-30 142603" width="32%" />
  <img src="https://github.com/user-attachments/assets/15bac55e-a646-4771-9bf4-e90af7164d77" width="32%"/>
  <img src="https://github.com/user-attachments/assets/99030577-fdff-4780-a9f5-e58ced258897" alt="스크린샷 2025-10-30 142652" width="32%" />
</p>

## 📋 프로젝트 개요


사용자의 스마트폰 사용 데이터를 수집, 분석하여 의미 있는 인사이트를 제공하는 MSA 기반 백엔드 서비스입니다.

### 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    Client (Mobile)                      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Cloud Gateway (8088)                │
│          - Routing                                      │
│          - Load Balancing                               │
│          - API Aggregation                              │
└───┬──────────────────┬──────────────────┬───────────────┘
    │                  │                  │
    ▼                  ▼                  ▼
┌─────────┐    ┌──────────────┐   ┌─────────────┐
│  User   │    │    Usage     │   │  Insight    │
│ Service │    │   Service    │   │  Service    │
│ (8080)  │    │   (8080)     │   │  (8080)     │
└────┬────┘    └──────┬───────┘   └──────┬──────┘
     │                │                   │
     ▼                ▼                   ▼
┌────────────────────────────────────────────────────────┐
│                Infrastructure Layer                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │PostgreSQL│  │  Redis   │  │  Kafka (Event Bus)   │  │
│  │   (DB)   │  │ (Cache)  │  │  - user_signup       │  │
│  │          │  │          │  │  - user_login        │  │
│  │          │  │          │  │  - usage_events      │  │
│  └──────────┘  └──────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

## 🚀 서비스 구성

### 1. **UserService** (포트: 8080)
- 사용자 인증 및 관리
- JWT 기반 인증
- Redis 세션 관리 (DB 0)
- Kafka 이벤트 발행 (user_signup, user_login, user_logout 등)

**주요 기능:**
- 회원가입/로그인
- 프로필 관리
- 비밀번호 변경
- 계정 비활성화

[상세 문서 →](./UserService/README.md)

---

### 2. **UsageService** (포트: 8080)
- 앱 사용 데이터 수집 및 분석
- 실시간 사용량 집계
- 일일 배치 처리
- Redis 캐싱 (DB 1)

**주요 기능:**
- 앱 사용 이벤트 수집 (FOREGROUND/BACKGROUND)
- 실시간 통계 (24시간 슬라이딩 윈도우)
- 카테고리별 사용 시간 집계
- Top 3 앱 추적

[상세 문서 →](./UsageService/README.md)

---

### 3. **Gateway** (포트: 8088)
- Spring Cloud Gateway
- 라우팅 및 로드 밸런싱
- API 게이트웨이

**라우팅 규칙:**
- `/users/**` → UserService
- `/usage/**` → UsageService
- `/insights/**` → InsightService

[상세 문서 →](./gateway/README.md)

---

### 4. **InsightService** (포트: 8080)
- Python AI 모델링 프로젝트에서 이식된 통계 기반 사용자 분류 로직을 담당합니다.
- 사용자 패턴 분석 및 인사이트 생성

[상세 문서 →](./InsightService/README.md)

---

## 🛠️ 기술 스택

| 분류         | 기술                                      |
|------------|-----------------------------------------|
| **언어**     | Java 21                                 |
| **프레임워크**  | Spring Boot 3.2.0, Spring Cloud Gateway |
| **빌드 도구**  | Gradle 8.5                              |
| **데이터베이스** | PostgreSQL 15, TimescaleDB              |
| **캐시**     | Redis 7                                 |
| **메시지 큐**  | Apache Kafka 3.6                        |
| **컨테이너**   | Docker, Kubernetes                      |
| **모니터링**   | Spring Actuator                         |

---

## 📦 빠른 시작

### 사전 요구사항

- Java 21
- Docker & Docker Compose
- Kubernetes (Docker Desktop 또는 Minikube)
- kubectl

### 1. 로컬 개발 환경 (Docker Compose)

```bash
# 인프라 서비스 실행 (PostgreSQL, Redis, Kafka)
docker-compose -f build-docker-compose.yml up -d
```

### 2. Kubernetes 배포

```bash
# 1. Docker 이미지 빌드
./build-images.sh

# 2. 네임스페이스 생성
kubectl apply -f k8s/namespace.yaml

# 3. Secrets 생성
kubectl apply -f k8s/secrets/

# 4. 데이터베이스 배포
kubectl apply -f k8s/databases/

# 5. 애플리케이션 배포
kubectl apply -f k8s/apps/

# 6. 배포 상태 확인
kubectl get pods -n screentimeai
kubectl get svc -n screentimeai
```

### 3. API 테스트

```bash
# Gateway를 통한 사용자 등록
curl -X POST http://localhost:30088/users/signup \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "testuser",
    "password": "password123",
    "category": "STUDENT"
  }'

# 로그인
curl -X POST http://localhost:30088/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "testuser",
    "password": "password123"
  }'
```

---

## 🗂️ 프로젝트 구조

```
backend/
├── UserService/           # 사용자 서비스
│   ├── src/
│   ├── build.gradle
│   ├── Dockerfile
│   └── README.md
├── UsageService/          # 사용량 추적 서비스
│   ├── src/
│   ├── build.gradle
│   ├── Dockerfile
│   └── README.md
├── InsightService/        # 인사이트 서비스 (진행중)
│   ├── src/
│   ├── build.gradle
│   └── README.md
├── gateway/               # API Gateway
│   ├── src/
│   ├── build.gradle
│   ├── Dockerfile
│   └── README.md
├── k8s/                   # Kubernetes 배포 설정
│   ├── namespace.yaml
│   ├── secrets/
│   ├── databases/
│   ├── apps/
│   └── README.md
├── scripts/               # 유틸리티 스크립트
├── build-images.sh        # Docker 이미지 빌드 스크립트
├── build-docker-compose.yml
├── settings.gradle        # Multi-module Gradle 설정
└── README.md              # 이 문서
```

---

### 환경 변수

각 서비스의 `application.yml`에서 다음 환경 변수를 사용:

| 환경 변수                            | 설명         | 기본값              |
|----------------------------------|------------|------------------|
| `SPRING_PROFILES_ACTIVE`         | 활성 프로파일    | `default`        |
| `SPRING_DATA_REDIS_HOST`         | Redis 호스트  | `localhost`      |
| `SPRING_DATA_REDIS_PORT`         | Redis 포트   | `6379`           |
| `SPRING_DATA_REDIS_PASSWORD`     | Redis 비밀번호 | -                |
| `SPRING_DATASOURCE_URL`          | DB 연결 URL  | -                |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커  | `localhost:9092` |

---

## 📊 모니터링 & 헬스체크

### Health Check Endpoints

각 서비스는 Spring Actuator를 통해 헬스 체크 엔드포인트를 제공합니다:

```bash
# UserService
curl http://localhost:8081/actuator/health

# UsageService
curl http://localhost:8082/actuator/health

# Gateway
curl http://localhost:8088/actuator/health
```

### Kubernetes에서 확인

```bash
# Pod 상태 확인
kubectl get pods -n screentimeai

# 서비스 로그 확인
kubectl logs -f <pod-name> -n screentimeai

# Pod 디버깅
kubectl describe pod <pod-name> -n screentimeai
```

---

## 🧪 테스트

### UserService 테스트

⚠️ **중요**: UserService 테스트를 실행하기 전에 **Kafka, Redis, PostgreSQL**이 실행 중이어야 합니다.

```bash
# 인프라 서비스 실행
docker-compose -f build-docker-compose.yml up -d

# 테스트 실행
cd UserService
./gradlew test
```

### UsageService 테스트

```bash
cd UsageService
./gradlew test
```

### 전체 테스트

```bash
# 루트 디렉토리에서
./gradlew test
```

---

## 🚀 배포 가이드

### 로컬 Kubernetes 배포

```bash
# 1. 이미지 빌드
./build-images.sh

# 2. Kubernetes 리소스 적용
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/databases/
kubectl apply -f k8s/apps/

# 3. 배포 확인
kubectl get all -n screentimeai
kubectl get all -n db
```

### 서비스 접근

```bash
# Gateway (NodePort)
curl http://localhost:30088/users/health

# Port Forward로 개별 서비스 접근
kubectl port-forward svc/userservice 8081:8080 -n screentimeai
curl http://localhost:8081/actuator/health
```

---

## 📚 추가 문서

- **Event Storming Model**: [MSAez](https://www.msaez.io/#/12370384/storming/0b64f0dc4e6020e94b57cbf7849801b4)
- **Database Setup**: [README_DB.md](./README_DB.md)
- **Kubernetes Guide**: [k8s/README.md](./k8s/README.md)
- **UserService**: [UserService/README.md](./UserService/README.md)
- **UsageService**: [UsageService/README.md](./UsageService/README.md)
- **Gateway**: [gateway/README.md](./gateway/README.md)

---


---

## 🔄 개발 워크플로우

### 코드 변경 후 재배포

```bash
# 1. 이미지 재빌드
./build-images.sh

# 2. Deployment 재시작 (이미지는 latest 태그 사용)
kubectl rollout restart deployment userservice -n screentimeai
kubectl rollout restart deployment usageservice -n screentimeai
kubectl rollout restart deployment gateway -n screentimeai

# 3. 재시작 상태 확인
kubectl rollout status deployment userservice -n screentimeai
```

### ConfigMap 변경

```bash
# ConfigMap 수정 후 적용
kubectl apply -f k8s/apps/userservice.yaml

# Pod 재시작 (ConfigMap 변경사항 반영)
kubectl rollout restart deployment userservice -n screentimeai
```

---

## 🤝 기여 가이드

1. Feature 브랜치 생성 (`git checkout -b feature/amazing-feature`)
2. 변경 사항 커밋 (`git commit -m 'Add amazing feature'`)
3. 브랜치 푸시 (`git push origin feature/amazing-feature`)
4. Pull Request 생성

---

## 📝 버전 히스토리

### v1.0.0 (2025-10-18)
- ✅ UserService: 사용자 인증 및 관리
- ✅ UsageService: 앱 사용 데이터 수집 및 분석
- ✅ Gateway: API Gateway 구성
- ✅ Kubernetes 배포 설정
- ✅ Redis 연결 문제 해결 (Config 클래스 password 설정)
- ✅ Multi-module Gradle 프로젝트 구성

### v1.1.0 (예정)
- ⏳ Istio Service Mesh 도입
- ⏳ Istio Gateway 및 VirtualService 구성
- ⏳ 트래픽 관리 및 서비스 간 mTLS
- ⏳ InsightService 구현

---

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.

---

## 👥 팀

- Backend Development
- Infrastructure & DevOps
- Data Analysis

---

## 📞 문의

프로젝트 관련 문의사항은 이슈를 생성해 주세요.
