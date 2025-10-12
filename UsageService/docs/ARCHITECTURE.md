# UsageService Architecture

## Overview
UsageService는 사용자의 앱 사용 패턴을 실시간으로 추적하고 일일 집계 데이터를 생성하는 서비스입니다.

## 핵심 설계 원칙

### 1. 2-Tier 데이터 아키텍처
```
RawData (TimescaleDB) → Redis (실시간 집계) → PostgreSQL (일일 집계)
```

**Redis 계층:**
- 실시간 메트릭 계산 (24시간 슬라이딩 윈도우)
- 일일 메트릭 누적 (00:00-23:59, utcoffset 고려)
- TTL: 48시간 (안전 마진)

**PostgreSQL 계층:**
- daily_user_metrics: 사용자별 일일 종합 메트릭
- daily_category_usage: 카테고리별 사용 시간
- daily_top_apps: Top 3 앱

### 2. 실제 앱 사용시간 계산 로직

#### 이벤트 기반 계산
```
MOVE_TO_FOREGROUND → 앱 시작 시간 저장 (Redis)
MOVE_TO_BACKGROUND → 종료 시간과의 차이 계산 → 집계
```

**Key Design:**
- `foreground_app:{subjectId}` → 현재 포그라운드 앱 추적
- 비정상 데이터 필터링: 0ms 미만 또는 24시간 초과

#### 시간대 분류 (utcoffset 고려)
```
Dawn:      00:00-05:59
Morning:   06:00-11:59
Afternoon: 12:00-17:59
Evening:   18:00-23:59
```

### 3. Redis 데이터 구조

#### Daily Metrics (00:00 기준)
```
Key: daily_metrics:{subjectId}:{date}
Hash Fields:
  - total_usage_minutes
  - dawn_minutes, morning_minutes, afternoon_minutes, evening_minutes
  - dawn_sns_minutes
  - daily_pickup_count
  - hour_0 ~ hour_23 (most_active_hour 계산용)
  - category_1 ~ category_5 (카테고리별 사용시간)
  - app:{packageName} (앱별 사용시간)
```

#### Realtime Metrics (24시간 슬라이딩 윈도우)
```
Key: realtime_metrics:{subjectId}:events
Type: Sorted Set
Members: {packageName}:{categoryId}:{usageMinutes}
Score: timestamp (epoch millis)

Key: realtime_metrics:{subjectId}:stats
Hash Fields:
  - top_app_1, top_app_1_minutes
  - top_app_2, top_app_2_minutes
  - top_app_3, top_app_3_minutes
  - game_ratio, social_comm_ratio, learning_ratio, entertainment_ratio
```

#### Foreground App Tracking
```
Key: foreground_app:{subjectId}
Hash Fields:
  - packageName
  - startTimestamp
  - utcOffset
TTL: 24 hours
```

### 4. 카테고리 매핑

**Category IDs:**
1. GAME
2. SOCIAL_COMM (Social, Communication, Dating)
3. LEARNING (Education, Books & Reference)
4. ENTERTAINMENT (Entertainment, Music, Video Players, Comics)
5. OTHER

**Data Source:** `all_apps.csv` (14,445 apps)
- 애플리케이션 시작 시 메모리에 로드
- HashMap으로 O(1) 조회

### 5. Batch Job (매일 00:00)

**Process Flow:**
```
1. Redis에서 어제 날짜 키 조회 (daily_metrics:*:2025-10-10)
2. 각 subject_id별로:
   a. DailyUserMetrics 생성 및 저장
      - most_active_hour 계산
      - 카테고리 비율 계산
   b. DailyCategoryUsage 생성 및 저장 (카테고리별)
   c. DailyTopApps 생성 및 저장 (Top 3)
3. Redis 데이터 삭제
```

**Cron:** `0 0 0 * * *` (매일 자정)

## 주요 컴포넌트

### AppUsageTracker
- **책임:** 앱 사용 이벤트 처리 및 Redis 집계
- **핵심 메서드:**
  - `processEvent()`: FOREGROUND/BACKGROUND/SCREEN_ON 처리
  - `handleForegroundEvent()`: 앱 시작 저장
  - `handleBackgroundEvent()`: 사용시간 계산 및 집계
  - `aggregateUsageTime()`: 모든 메트릭 집계

### CategoryMappingService
- **책임:** Package name → Category ID 매핑
- **초기화:** `@PostConstruct`로 CSV 로드
- **성능:** HashMap 기반 O(1) 조회

### DailyMetricsBatchService
- **책임:** 자정 배치 작업 실행
- **트랜잭션:** 각 subject_id별로 독립 처리 (부분 실패 허용)

### DailyMetricsAggregator
- **책임:** RawDataController와 AppUsageTracker 사이의 파사드
- **목적:** 기존 코드 호환성 유지

## 데이터 흐름

```
┌─────────────┐
│ RawData API │
└──────┬──────┘
       │
       ▼
┌──────────────────────┐
│ RawDataController    │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ DailyMetricsAggregator│
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐     ┌────────────────────┐
│ AppUsageTracker      │────→│ CategoryMapping    │
└──────┬───────────────┘     └────────────────────┘
       │
       ▼
┌──────────────────────┐
│ Redis                │
│ - Daily Metrics      │
│ - Realtime Metrics   │
│ - Foreground App     │
└──────┬───────────────┘
       │
       │ (매일 00:00)
       ▼
┌──────────────────────┐
│ DailyMetricsBatch    │
│ Service              │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ PostgreSQL           │
│ - DailyUserMetrics   │
│ - DailyCategoryUsage │
│ - DailyTopApps       │
└──────────────────────┘
```

## 중요한 고려사항

### 1. UTC Offset 처리
- 사용자별로 다른 타임존 지원
- LocalDate 계산 시 utcoffset 적용
- 00:00 기준은 사용자의 로컬 시간 기준

### 2. 데이터 정합성
- FOREGROUND 이벤트만 있고 BACKGROUND가 없는 경우 → 24시간 후 자동 삭제
- 비정상 사용시간 필터링 (음수, 24시간 초과)
- Redis TTL로 메모리 관리

### 3. 성능 최적화
- Redis Hash 사용으로 Atomic 연산
- Sorted Set으로 24시간 윈도우 관리
- Batch Insert로 DB 저장

### 4. 확장성
- Subject ID별 독립 처리
- Redis 샤딩 가능
- 배치 작업 병렬화 가능

## 개선 포인트

### 현재 이슈
1. ~~RawData의 is_system_app 타입 (Integer vs Boolean)~~ → 수정 필요
2. ~~카테고리 판단이 type 필드가 아닌 package_name 기반이어야 함~~ → 구현 완료
3. ~~Top 3 앱 추적 로직 누락~~ → 구현 완료
4. ~~MOVE_TO_FOREGROUND/BACKGROUND 기반 시간 계산 누락~~ → 구현 완료

### 향후 개선 사항
1. **분산 락:** Redis 동시성 제어 (같은 사용자의 동시 이벤트 처리)
2. **모니터링:** 배치 작업 실패 알림
3. **데이터 복구:** Redis 장애 시 RawData에서 재집계
4. **성능 테스트:** 대용량 데이터 처리 벤치마크
5. **실시간 알림:** 특정 패턴 감지 (새벽 사용 등)

## 테스트 전략

### 단위 테스트
- CategoryMappingService: 카테고리 매핑 정확도
- AppUsageTracker: 시간 계산 로직
- DailyMetricsBatchService: 배치 변환 로직

### 통합 테스트
- Redis 연동 테스트
- 전체 파이프라인 테스트 (이벤트 → Redis → DB)
- 24시간 슬라이딩 윈도우 테스트

### 시나리오 테스트
1. 정상 사용 패턴 (FOREGROUND → BACKGROUND)
2. 비정상 패턴 (BACKGROUND만 있는 경우)
3. 자정 경계 케이스
4. 타임존 변경 케이스
