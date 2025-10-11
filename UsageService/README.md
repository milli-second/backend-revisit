# UsageService - 앱 사용량 추적 서비스

## 📋 프로젝트 개요

사용자의 앱 사용 패턴을 실시간으로 추적하고 일일 집계 데이터를 생성하는 서비스입니다.

- **TimescaleDB**: 원시 데이터 저장
- **Redis**: 실시간 집계 및 세션 관리
- **PostgreSQL**: 일일 집계 데이터 저장

---

## ✅ 구현 완료 항목

### Phase 1: 기본 구조 및 Entity ✅
- [x] RawData Entity 및 Repository
- [x] DailyUserMetrics Entity 및 Repository
- [x] DailyCategoryUsage Entity 및 Repository
- [x] DailyTopApps Entity 및 Repository
- [x] 복합키 ID 클래스 구현

### Phase 2: 카테고리 매핑 시스템 ✅
- [x] CategoryMappingService 구현
- [x] all_apps.csv 파일 로드 (14,445개 앱)
- [x] package_name → category_id 매핑 (O(1) 조회)
- [x] 5개 카테고리 분류: GAME, SOCIAL_COMM, LEARNING, ENTERTAINMENT, OTHER

### Phase 3: 기본 앱 사용시간 계산 
- [ ] AppUsageTracker 서비스 구현
- [ ] MOVE_TO_FOREGROUND 이벤트 처리
- [ ] MOVE_TO_BACKGROUND 이벤트 처리
- [ ] 실제 사용시간 계산 (시작-종료)
- [ ] 비정상 데이터 필터링 (음수, 24시간 초과)
- [ ] SCREEN_ON 이벤트 처리 (픽업 카운트)

### Phase 4: Redis 실시간 집계 
- [ ] 일일 메트릭 집계 (00:00-23:59, UTC offset 고려)
  - [ ] 총 사용시간
  - [ ] 시간대별 사용시간 (dawn/morning/afternoon/evening)
  - [ ] 새벽 SNS 사용시간
  - [ ] 시간별 사용량 (hour_0 ~ hour_23)
  - [ ] 카테고리별 사용시간
  - [ ] 앱별 사용시간
- [ ] 실시간 메트릭 (24시간 슬라이딩 윈도우)
  - [ ] Sorted Set으로 이벤트 저장
  - [ ] 24시간 이전 데이터 자동 삭제
  - [ ] Top 3 앱 계산
  - [ ] 카테고리 비율 계산

### Phase 5: 일일 배치 작업 
- [ ] DailyMetricsBatchService 구현
- [ ] 매일 00:00 Cron 스케줄러
- [ ] Redis → DB 저장
  - [ ] DailyUserMetrics 저장
  - [ ] DailyCategoryUsage 저장 (카테고리별)
  - [ ] DailyTopApps 저장 (Top 3)
- [ ] Most Active Hour 계산
- [ ] 카테고리 비율 계산
- [ ] 부분 실패 허용 (한 사용자 실패해도 다른 사용자는 저장)

### Phase 6: 테스트 코드 
- [ ] AppUsageTrackerTest
  - [ ] FOREGROUND/BACKGROUND 이벤트 처리
  - [ ] 비정상 사용시간 필터링
  - [ ] SCREEN_ON 이벤트
  - [ ] 자정 경계 케이스 (UTC offset)
  - [ ] 24시간 슬라이딩 윈도우
- [x] CategoryMappingServiceTest
  - [x] CSV 파일 로드
  - [x] 카테고리 매핑 정확도
  - [x] NULL 처리
- [ ] DailyMetricsBatchServiceTest
  - [ ] 배치 변환 로직
  - [ ] Top 3 앱 추출
  - [ ] Most Active Hour 계산
  - [ ] 부분 실패 허용

---

## 🚧 진행 중 / 미완료 항목

### Phase 7: 세션 관리 강화 (권장 개선 사항) ⚠️ 높은 우선순위
> **현재 문제점**: BACKGROUND 이벤트 누락 시 24시간 후에야 정리됨 (너무 늦음)

- [ ] **TTL 기반 세션 관리**
  - [ ] Foreground 세션 키에 30분 TTL 설정
  - [ ] FOREGROUND 이벤트 시 이전 세션 확인 로직 추가
  - [ ] 이전 세션이 남아있으면 비정상 종료로 간주하고 집계
  - [ ] `handleAbnormalTermination()` 메서드 구현

- [ ] **Heartbeat 패턴 구현**
  - [ ] 클라이언트: 5분마다 USER_INTERACTION 이벤트 전송
  - [ ] 서버: lastHeartbeat 필드 갱신
  - [ ] TTL 자동 연장 로직 (EXPIRE 명령어)
  - [ ] 장시간 사용 대응 (30분 이상)

- [ ] **비정상 종료 시나리오 테스트**
  - [ ] FOREGROUND만 있고 BACKGROUND 없는 경우
  - [ ] 앱 강제 종료 시뮬레이션
  - [ ] 배터리 방전 시나리오
  - [ ] 네트워크 끊김 시나리오

### Phase 8: 동시성 제어 및 안정성 ⏳
> **현재 문제점**: 같은 사용자의 동시 이벤트 처리 시 경쟁 조건 발생 가능

- [ ] **Redis 분산 락**
  - [ ] Redisson 라이브러리 추가 (build.gradle)
  - [ ] `@RedissonLock` 어노테이션 기반 락 구현
  - [ ] 락 타임아웃 설정 (3초)
  - [ ] 재시도 로직 (3회)
  - [ ] 락 획득 실패 시 예외 처리

- [ ] **배치 작업 모니터링**
  - [ ] 배치 실패 시 Slack 알림
  - [ ] 성공/실패 통계를 별도 테이블에 기록
  - [ ] 배치 실행 시간 측정 및 로깅
  - [ ] 재시도 로직 (실패 시 1회 재시도)

### Phase 9: 빌드 및 배포 ⏳
- [ ] **Gradle 설정**
  - [ ] Gradle Wrapper 생성
  ```bash
  gradle wrapper
  ```
  - [ ] build.gradle 의존성 확인
  - [ ] 빌드 테스트
  ```bash
  ./gradlew clean build
  ```

- [ ] **Docker 컨테이너화**
  - [ ] Dockerfile 작성
  - [ ] docker-compose.yml 업데이트
    - [ ] Redis 컨테이너
    - [ ] PostgreSQL 컨테이너
    - [ ] TimescaleDB 컨테이너
    - [ ] UsageService 컨테이너
  - [ ] 컨테이너 간 네트워크 설정
  - [ ] 볼륨 마운트 설정

- [ ] **환경 변수 관리**
  - [ ] application-dev.yml 작성
  - [ ] application-prod.yml 작성
  - [ ] 민감 정보 환경 변수화
    - [ ] DB 패스워드
    - [ ] Redis 패스워드
    - [ ] API 키 등

### Phase 10: 통합 테스트 및 검증 ⏳
- [ ] **API 테스트**
  - [ ] Postman 컬렉션 작성
  - [ ] POST /api/raw-data 테스트
    - [ ] FOREGROUND 이벤트 전송
    - [ ] BACKGROUND 이벤트 전송
    - [ ] SCREEN_ON 이벤트 전송
  - [ ] GET /api/raw-data/realtime-stats/{subjectId} 테스트
  - [ ] 응답 시간 측정 (< 100ms 목표)

- [ ] **Redis 데이터 검증**
  - [ ] redis-cli로 키 구조 확인
  ```bash
  redis-cli
  > keys foreground_app:*
  > hgetall foreground_app:{subjectId}
  > keys daily_metrics:*
  > hgetall daily_metrics:{subjectId}:{date}
  ```
  - [ ] TTL 확인
  - [ ] 데이터 타입 확인

- [ ] **DB 데이터 검증**
  - [ ] DailyUserMetrics 데이터 확인
  - [ ] DailyCategoryUsage 데이터 확인
  - [ ] DailyTopApps 데이터 확인
  - [ ] 배치 작업 실행 후 검증

- [ ] **시나리오 테스트**
  - [ ] 정상 사용 패턴
    - [ ] FOREGROUND (09:00) → BACKGROUND (09:30) = 30분 사용
  - [ ] 비정상 종료 패턴
    - [ ] FOREGROUND만 전송 → 30분 후 TTL 만료 확인
  - [ ] 장시간 사용 패턴
    - [ ] FOREGROUND → Heartbeat (5분마다) → BACKGROUND (1시간 후)
  - [ ] 자정 경계 케이스
    - [ ] FOREGROUND (23:50) → BACKGROUND (00:10)
    - [ ] 두 날짜에 각각 집계되는지 확인

### Phase 11: 성능 최적화 및 모니터링 📊
- [ ] **성능 테스트**
  - [ ] JMeter 시나리오 작성
    - [ ] 동시 사용자 100명
    - [ ] 각 사용자당 10개 이벤트
    - [ ] 총 1,000 requests/sec
  - [ ] Redis 메모리 사용량 측정
    - [ ] INFO memory 명령어로 확인
    - [ ] 1만 사용자 기준 예상 메모리 계산
  - [ ] DB 쿼리 성능 분석
    - [ ] EXPLAIN ANALYZE로 쿼리 플랜 확인
    - [ ] 인덱스 최적화

- [ ] **모니터링 설정**
  - [ ] Spring Actuator 활성화
    - [ ] /actuator/health
    - [ ] /actuator/metrics
    - [ ] /actuator/prometheus
  - [ ] Prometheus 연동
    - [ ] prometheus.yml 설정
    - [ ] UsageService 메트릭 수집
  - [ ] Grafana 대시보드
    - [ ] API 응답 시간
    - [ ] Redis 메모리 사용량
    - [ ] 배치 작업 성공/실패율
    - [ ] 처리량 (events/sec)

- [ ] **로그 관리**
  - [ ] Logback 설정
  - [ ] 로그 레벨 최적화 (운영: INFO, 개발: DEBUG)
  - [ ] 에러 로그 별도 파일 저장

### Phase 12: 장애 복구 및 재집계 🔧
- [ ] **데이터 복구 프로세스**
  - [ ] RawData에서 재집계 API 구현
    - [ ] POST /api/admin/recalculate
    - [ ] 파라미터: startDate, endDate, subjectId
  - [ ] 특정 날짜 범위 재계산
  - [ ] 기존 데이터 백업 후 덮어쓰기
  - [ ] 재집계 진행 상황 로그

- [ ] **Redis 장애 대응**
  - [ ] Redis 연결 실패 시 재시도 로직
  - [ ] Fallback: RawData에 직접 저장만 하고 집계는 나중에
  - [ ] Redis 복구 후 자동 재집계

---

## 🏗️ 아키텍처

```
┌─────────────────┐
│  Mobile Client  │
│                 │
│  - 5분마다        │ ← Heartbeat 패턴 추가 예정
│    Heartbeat    │
└────────┬────────┘
         │ POST /api/raw-data
         ▼
┌─────────────────┐
│ RawDataController│
└────────┬────────┘
         │
         ▼
┌─────────────────────┐     ┌──────────────────┐
│ AppUsageTracker     │────→│ CategoryMapping  │
└────────┬────────────┘     └──────────────────┘
         │
         ▼
┌─────────────────────┐
│      Redis          │
│ ┌─────────────────┐ │
│ │ Foreground App  │ │ TTL: 30분 ← 개선 예정
│ ├─────────────────┤ │
│ │ Daily Metrics   │ │ TTL: 48시간
│ ├─────────────────┤ │
│ │ Realtime Stats  │ │ TTL: 48시간
│ └─────────────────┘ │
└────────┬────────────┘
         │ (매일 00:00)
         ▼
┌─────────────────────┐
│ Batch Job (Cron)    │
└────────┬────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│          PostgreSQL                 │
│ ┌─────────────────────────────────┐ │
│ │ DailyUserMetrics                │ │
│ │ DailyCategoryUsage              │ │
│ │ DailyTopApps                    │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

---

## 🚀 빠른 시작

### 1. 빌드

```bash
# Gradle Wrapper 생성 (필요 시)
gradle wrapper

# 빌드
./gradlew clean build

# 테스트 제외 빌드
./gradlew clean build -x test
```

### 2. 로컬 실행

```bash
# Redis & PostgreSQL 실행 (Docker Compose)
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

### 3. API 테스트

```bash
# FOREGROUND 이벤트 전송
curl -X POST http://localhost:8080/api/raw-data \
  -H "Content-Type: application/json" \
  -d '[
    {
      "subject_id": "2099efc17d719274924b8c81543c7972",
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

# 30분 후 BACKGROUND 이벤트 전송
curl -X POST http://localhost:8080/api/raw-data \
  -H "Content-Type: application/json" \
  -d '[
    {
      "subject_id": "2099efc17d719274924b8c81543c7972",
      "timestamp": 1728640789686,
      "utcoffset": 9,
      "source_type": "PHONE",
      "source_info": "samsung-SM-G928S-7.0",
      "package_name": "com.nhn.android.band",
      "name": "BAND",
      "is_system_app": 0,
      "is_updated_system_app": 0,
      "type": "MOVE_TO_BACKGROUND"
    }
  ]'

# 실시간 통계 조회
curl http://localhost:8080/api/raw-data/realtime-stats/2099efc17d719274924b8c81543c7972
```

---

## 📊 Redis 데이터 구조

### 1. Foreground App (세션 관리)
```
Key: foreground_app:{subjectId}
Type: Hash
TTL: 30분 (비정상 종료 감지용) ← 개선 예정

Fields:
  packageName: "com.example.app"
  startTimestamp: 1728638989686
  utcOffset: 9
  lastHeartbeat: 1728639289686  ← Heartbeat 패턴용 (추가 예정)
```

### 2. Daily Metrics (일일 집계)
```
Key: daily_metrics:{subjectId}:{date}
Type: Hash
TTL: 48시간

Fields:
  total_usage_minutes: 120
  dawn_minutes: 30
  morning_minutes: 40
  afternoon_minutes: 30
  evening_minutes: 20
  dawn_sns_minutes: 15
  daily_pickup_count: 50
  hour_0 ~ hour_23: 각 시간별 사용량
  category_1 ~ category_5: 카테고리별 사용시간
  app:{packageName}: 앱별 사용시간
```

### 3. Realtime Metrics (24시간 윈도우)
```
Key: realtime_metrics:{subjectId}:events
Type: Sorted Set
TTL: 48시간
Members: "{packageName}:{categoryId}:{usageMinutes}"
Score: timestamp

Key: realtime_metrics:{subjectId}:stats
Type: Hash
TTL: 48시간
Fields:
  top_app_1, top_app_1_minutes
  top_app_2, top_app_2_minutes
  top_app_3, top_app_3_minutes
  game_ratio, social_comm_ratio, learning_ratio, entertainment_ratio
```

---

## 🧪 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 실행
./gradlew test --tests AppUsageTrackerTest

# 특정 테스트 메서드 실행
./gradlew test --tests AppUsageTrackerTest.testHandleForegroundEvent

# 테스트 커버리지 리포트
./gradlew test jacocoTestReport
```

---

## 📚 문서

- **ARCHITECTURE.md**: 전체 아키텍처 상세 설명
- **IMPLEMENTATION_SUMMARY.md**: 구현 완료 요약
- **test-raw-data.http**: HTTP 테스트 파일

---

## 🐛 알려진 이슈 및 개선 필요 사항

### 1. 세션 관리 개선 필요 (우선순위: 🔴 높음) ⚠️
**문제**:
- 현재 BACKGROUND 이벤트가 누락되면 24시간 후에야 정리됨 (너무 늦음)
- 앱 강제 종료, 배터리 방전 등 비정상 종료 케이스 대응 부족
- 30분 이상 사용 시 중간 체크포인트 없음

**해결 방안**:
- ✅ TTL 30분 설정으로 비정상 종료 빠르게 감지
- ✅ FOREGROUND 시 이전 세션 확인 및 정리
- ✅ Heartbeat 패턴으로 장시간 사용 대응
  - 클라이언트: 5분마다 USER_INTERACTION 이벤트
  - 서버: lastHeartbeat 갱신 및 TTL 연장

**작업 항목**: Phase 7 참조

### 2. 동시성 제어 부재 (우선순위: 🟡 중간) ⚠️
**문제**:
- 같은 사용자의 동시 이벤트 처리 시 경쟁 조건 발생 가능
- 예: FOREGROUND와 BACKGROUND가 동시에 들어오는 경우

**해결 방안**:
- Redis 분산 락 적용 (Redisson)
- Subject ID 기준으로 락 획득
- 타임아웃 3초, 재시도 3회

**작업 항목**: Phase 8 참조

### 3. 배치 작업 모니터링 부족 (우선순위: 🟡 중간)
**문제**:
- 배치 실패 시 알림 없음
- 성공/실패 통계 없음

**해결 방안**:
- Slack 알림 추가
- 배치 실행 이력 테이블 생성
- 재시도 로직

**작업 항목**: Phase 8 참조

---

## 🔧 환경 변수

```yaml
# application.yml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/usagedb}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}

logging:
  level:
    screentimeai: ${LOG_LEVEL:INFO}

# Batch 설정
batch:
  cron: ${BATCH_CRON:0 0 0 * * *}  # 매일 00:00

# Session 관리
session:
  ttl-minutes: ${SESSION_TTL:30}
  heartbeat-interval-minutes: ${HEARTBEAT_INTERVAL:5}
```

---

## 📈 성능 목표

| 항목 | 목표 | 현재 상태 |
|------|------|-----------|
| API 응답 시간 | < 100ms | ⏳ 측정 필요 |
| 처리량 | 1,000 req/sec | ⏳ 측정 필요 |
| Redis 메모리 | < 1GB (1만 사용자) | ⏳ 측정 필요 |
| 배치 실행 시간 | < 5분 (1만 사용자) | ⏳ 측정 필요 |

---

## 👥 Contributing

1. 이슈 생성
2. Feature 브랜치 생성 (`feature/session-management`)
3. 테스트 코드 작성
4. Pull Request 생성

---

## 📝 버전 히스토리

### v1.0.0 (2025-10-11) 
- ⏳ 기본 앱 사용시간 계산 로직 구현
- ⏳ Redis 실시간 집계
- ⏳ 일일 배치 작업
- ⏳ 카테고리 매핑 시스템 (14,445개 앱)
- ⏳ Top 3 앱 추적
- ⏳ UTC Offset 지원
- ⏳ 테스트 코드 작성

### v1.1.0 (예정 - 우선순위 높음) 🚧
- ⏳ TTL 기반 세션 관리
- ⏳ Heartbeat 패턴
- ⏳ 비정상 종료 처리 강화
- ⏳ Redis 분산 락

### v1.2.0 (예정) 📋
- ⏳ 배치 작업 모니터링
- ⏳ 성능 최적화
- ⏳ 재집계 API

