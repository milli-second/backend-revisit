# Gateway Service

이 서비스는 마이크로서비스를 위한 API 게이트웨이 역할을 하며, 적절한 백엔드 서비스로 요청을 라우팅하고 인증, 권한 부여 및 속도 제한과 같은 공통 기능을 처리합니다.

## 📋 역할

- 모든 클라이언트 요청의 **단일 진입점**
- 서비스별 **라우팅**
- **로드 밸런싱**
- 요청/응답 **로깅**

## 🛠️ 기술 스택

- **Java 21** + **Spring Cloud Gateway**
- **Spring Boot 3.2.0**

## 🚦 라우팅 규칙

| 경로 | 대상 서비스 | 설명 |
|------|-------------|------|
| `/users/**` | UserService:8080 | 사용자 인증/관리 |
| `/usage/**` | UsageService:8080 | 앱 사용량 추적 |
| `/insights/**` | InsightService:8080 | 인사이트 생성 |

## 🚀 빠른 시작

### 로컬 실행

```bash
# 실행
./gradlew bootRun

# 포트: 8088
```

### API 테스트

```bash
# Health Check
curl http://localhost:8088/actuator/health

# UserService 경유
curl -X POST http://localhost:8088/users/signup \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "testuser",
    "password": "password123",
    "category": "STUDENT"
  }'

# UsageService 경유
curl http://localhost:8088/usage/realtime-stats/user123
```

## 🏗️ 아키텍처

```
                     ┌─────────────┐
                     │   Client    │
                     └──────┬──────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  Gateway :8088          │
              │  - Route Matching       │
              │  - Load Balancing       │
              └─────────┬───────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ UserService  │ │UsageService  │ │InsightService│
│   :8080      │ │   :8080      │ │   :8080      │
└──────────────┘ └──────────────┘ └──────────────┘
```

## 🔧 설정

### application.yml

```yaml
server:
  port: 8088

spring:
  cloud:
    gateway:
      routes:
        # UserService 라우팅
        - id: userservice
          uri: ${USERSERVICE_URI:http://localhost:8081}
          predicates:
            - Path=/users/**

        # UsageService 라우팅
        - id: usageservice
          uri: ${USAGESERVICE_URI:http://localhost:8082}
          predicates:
            - Path=/usage/**

        # InsightService 라우팅
        - id: insightservice
          uri: ${INSIGHTSERVICE_URI:http://localhost:8083}
          predicates:
            - Path=/insights/**
```

### 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `USERSERVICE_URI` | UserService URL | `http://localhost:8081` |
| `USAGESERVICE_URI` | UsageService URL | `http://localhost:8082` |
| `INSIGHTSERVICE_URI` | InsightService URL | `http://localhost:8083` |

### Kubernetes 환경

```yaml
USERSERVICE_URI: "http://userservice.screentimeai.svc.cluster.local:8080"
USAGESERVICE_URI: "http://usageservice.screentimeai.svc.cluster.local:8080"
INSIGHTSERVICE_URI: "http://insightservice.screentimeai.svc.cluster.local:8080"
```

## 📊 주요 기능

### 1. 경로 기반 라우팅

```
Client → /users/login → Gateway → UserService
Client → /usage/stats → Gateway → UsageService
```

### 2. 헬스 체크

```bash
# Gateway 자체 헬스 체크
curl http://localhost:8088/actuator/health

# Response
{
  "status": "UP"
}
```

### 3. CORS 설정

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
            allowedHeaders: "*"
```

## 🧪 테스트

```bash
# 빌드
./gradlew build

# 테스트
./gradlew test
```

## 📦 Docker & Kubernetes

### Docker 이미지 빌드

```bash
# 루트 디렉토리에서
./build-images.sh
```

### Kubernetes 배포

```bash
# 배포
kubectl apply -f ../k8s/apps/gateway.yaml

# 상태 확인
kubectl get pods -n screentimeai | grep gateway

# 서비스 확인
kubectl get svc -n screentimeai gateway
```

### 접근 방법

```bash
# NodePort로 접근 (로컬 Kubernetes)
curl http://localhost:30088/users/health

# Port Forward
kubectl port-forward svc/gateway 8088:8088 -n screentimeai
curl http://localhost:8088/users/health
```

## 🐛 트러블슈팅

### 503 Service Unavailable

**원인**: 대상 서비스가 실행 중이지 않음

**해결**:
```bash
# 서비스 상태 확인
kubectl get pods -n screentimeai

# 서비스 재시작
kubectl rollout restart deployment userservice -n screentimeai
```

### Connection Timeout

**원인**: 서비스 URI가 잘못됨

**해결**:
```bash
# ConfigMap 확인
kubectl get cm gateway-config -n screentimeai -o yaml

# 환경 변수 확인
kubectl exec -n screentimeai <gateway-pod> -- env | grep URI
```

## 📝 로깅

```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    reactor.netty: INFO
```

```bash
# 로그 확인
kubectl logs -f <gateway-pod> -n screentimeai

# 라우팅 로그 필터링
kubectl logs <gateway-pod> -n screentimeai | grep "Mapped"
```

## 🔮 향후 계획

### v1.1.0 (예정)
- ⏳ Rate Limiting (속도 제한)
- ⏳ Circuit Breaker (회로 차단기)
- ⏳ Request/Response 로깅 필터
- ⏳ JWT 인증 필터

### v1.2.0 (예정)
- ⏳ Istio Gateway 통합
- ⏳ mTLS 지원
- ⏳ 트래픽 분할 (Canary 배포)

## 📊 모니터링

```bash
# 메트릭 확인
curl http://localhost:8088/actuator/metrics

# 라우트 정보
curl http://localhost:8088/actuator/gateway/routes
```

## 📝 버전

- **v1.0.0** (2025-10-18)
  - ✅ 기본 라우팅 기능
  - ✅ UserService, UsageService 라우팅
  - ✅ Kubernetes 배포 지원
  - ✅ NodePort 외부 노출
