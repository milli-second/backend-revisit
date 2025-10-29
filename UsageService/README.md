# UsageService

앱 사용 데이터 수집 및 분석 서비스

## 📋 주요 기능

- 앱 사용 이벤트 수집 (FOREGROUND/BACKGROUND)
- 실시간 사용량 집계 (24시간 슬라이딩 윈도우)
- 일일 배치 처리 (매일 00:00)
- 카테고리별 사용 시간 집계
- Top 3 앱 추적

## 🛠️ 기술 스택

- **Java 21** + **Spring Boot 3.2.0**
- **PostgreSQL** - 일일 집계 데이터 저장
- **TimescaleDB** - 원시 데이터 저장 (시계열 DB)
- **Redis** (DB 1) - 실시간 집계 및 캐싱
- **Kafka** - 이벤트 구독 (usage_events)

## 🚀 빠른 시작

### 1. 로컬 실행


```bash
docker-compose -f ../build-docker-compose.yml up -d
```

### 2. API 테스트

```bash
# 앱 사용 이벤트 전송 (FOREGROUND)
curl -X POST http://localhost:8082/api/raw-data \
  -H "Content-Type: application/json" \
  -d '[
    {
      "subject_id": "user123",
      "timestamp": 1728638989686,
      "utcoffset": 9,
      "source_type": "PHONE",
      "source_info": "samsung-SM-G928S-7.0",
      "package_name": "com.nhn.android.band",
      "name": "BAND",
      "is_system_app": 0,
      "is_updated_system_app": 0,
      "type": "MOVE_TO_FOREGROUND"
    }
  ]'

# 실시간 통계 조회
curl http://localhost:8082/api/raw-data/realtime-stats/user123

# 7일 사용량 조회
curl http://localhost:8082/usage/weekly/user123
```

## 📡 API 엔드포인트

| 메서드 | 경로 | 설명 | 인증 필요 |
|--------|------|------|----------|
| POST | `/api/raw-data` | 앱 사용 이벤트 수집 | ❌ |
| GET | `/api/raw-data/realtime-stats/{userId}` | 실시간 통계 (24시간) | ❌ |
| GET | `/usage/weekly/{userId}` | 7일 사용량 조회 | ❌ |
| GET | `/usage/top-apps/{userId}` | Top 3 앱 조회 | ❌ |

## 🏗️ 아키텍처

```
┌──────────────┐
│ Mobile App   │
└──────┬───────┘
       │ POST /api/raw-data
       ▼
┌────────────────────────┐
│  RawDataController     │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐     ┌──────────────────┐
│  AppUsageTracker       │────→│ CategoryMapping  │
│  - FOREGROUND 처리      │     │ (14,445개 앱)     │
│  - BACKGROUND 처리      │     └──────────────────┘
│  - 사용시간 계산          │ 
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────┐
│      Redis (DB 1)      │
│  - Foreground 세션      │
│  - 일일 집계 (Hash)      │
│  - 실시간 통계 (ZSet)     │
└──────┬─────────────────┘
       │ (매일 00:00 Cron)
       ▼
┌────────────────────────┐
│  Batch Job             │
│  - Redis → DB 저장      │
│  - Top 3 계산           │
└──────┬─────────────────┘
       │
       ▼
┌────────────────────────────────┐
│       PostgreSQL               │
│  - DailyUserMetrics            │
│  - DailyCategoryUsage          │
│  - DailyTopApps                │
└────────────────────────────────┘
```

### 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `default` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis 포트 | `6379` |
| `SPRING_DATA_REDIS_DATABASE` | Redis DB 인덱스 | `1` |
| `SPRING_DATA_REDIS_PASSWORD` | Redis 비밀번호 | - |
| `SPRING_DATASOURCE_URL` | DB URL | `jdbc:postgresql://localhost:5433/usageservice` |

## 🧪 테스트

```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests AppUsageTrackerTest
```

## 📊 데이터 구조

### Redis (실시간 집계)

```
# Foreground 세션 (TTL: 30분)
foreground_app:{userId}
  - packageName
  - startTimestamp
  - utcOffset

# 일일 집계 (TTL: 48시간)
daily_metrics:{userId}:{date}
  - total_usage_minutes
  - dawn/morning/afternoon/evening_minutes
  - category_1 ~ category_5 (카테고리별)
  - app:{packageName} (앱별)

# 실시간 통계 (TTL: 48시간)
realtime_metrics:{userId}:events (Sorted Set)
realtime_metrics:{userId}:stats (Hash)
```

### PostgreSQL (장기 저장)

- **DailyUserMetrics**: 일일 사용자 메트릭
- **DailyCategoryUsage**: 카테고리별 사용 시간
- **DailyTopApps**: Top 3 앱 (일별)

## 📊 카테고리 매핑

14,445개의 앱을 5가지 카테고리로 분류:

| 카테고리 ID | 이름 | 예시 |
|------------|------|------|
| 1 | GAME | 게임 앱 |
| 2 | SOCIAL_COMM | SNS, 메신저 |
| 3 | LEARNING | 교육, 학습 |
| 4 | ENTERTAINMENT | 동영상, 음악 |
| 5 | OTHER | 기타 |

## 📦 빌드 & 배포

### Docker 이미지 빌드

```bash
# 루트 디렉토리에서
./build-images.sh
```
## 🛎️ [배포 안내 문서 ](../k8s/DEPLOY.md)

## 📚 추가 문서

- **[개발 진행 상황](./docs/DEVELOPMENT.md)** - 상세 구현 내역 및 TODO

## 📝 버전

- **v1.0.0** (2025-10-18)
  - ✅ 기본 사용량 추적
  - ✅ 실시간 집계 (Redis)
  - ✅ 일일 배치 처리
  - ✅ 카테고리 매핑 (14,445개 앱)
  - ✅ Top 3 앱 추적
  - ✅ Kubernetes 배포 지원

## 🔮 향후 계획

- ⏳ TTL 기반 세션 관리
- ⏳ Heartbeat 패턴 (장시간 사용 대응)
- ⏳ Redis 분산 락 (동시성 제어)
- ⏳ 배치 작업 모니터링
