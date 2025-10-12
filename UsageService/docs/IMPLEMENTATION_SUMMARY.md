# UsageService 구현 완료 요약

## 📋 구현 완료 항목

### ✅ 1. 데이터베이스 테이블 및 Entity 생성

#### 새로 생성된 Entity
- **DailyCategoryUsage**: 카테고리별 일일 사용량
  - subject_id, summary_date, category_id (복합키)
  - usage_minutes

- **DailyTopApps**: 일일 Top 3 앱
  - subject_id, summary_date, app_rank (복합키)
  - package_name, usage_minutes

#### 기존 Entity 확인
- **RawData**: TimescaleDB 원시 데이터 저장
  - ⚠️ is_system_app, is_updated_system_app은 Integer 타입 (JSON에서 0/1로 전달되므로 유지)

- **DailyUserMetrics**: 일일 사용자 메트릭
  - 모든 필드 타입 확인 완료

### ✅ 2. 카테고리 매핑 서비스 (CategoryMappingService)

#### 특징
- **데이터 소스**: `all_apps.csv` (14,445개 앱)
- **초기화**: `@PostConstruct`로 애플리케이션 시작 시 메모리 로드
- **성능**: HashMap 기반 O(1) 조회

#### 카테고리 매핑
```
1. GAME → GAME
2. SOCIAL_COMM → SOCIAL, COMMUNICATION, DATING
3. LEARNING → EDUCATION, BOOKS_AND_REFERENCE
4. ENTERTAINMENT → ENTERTAINMENT, MUSIC_AND_AUDIO, VIDEO_PLAYERS, COMICS
5. OTHER → 나머지
```

#### 주요 메서드
- `getCategoryId(packageName)`: package name → category ID
- `getCategoryName(categoryId)`: category ID → category name
- `isSocialApp(packageName)`: SNS 앱 여부 확인

### ✅ 3. 실제 앱 사용시간 계산 로직 (AppUsageTracker)

#### 핵심 원리
```
MOVE_TO_FOREGROUND 이벤트 → Redis에 시작 시간 저장
  ↓
MOVE_TO_BACKGROUND 이벤트 → 종료 시간과의 차이 계산
  ↓
실제 사용시간(분) 계산 → Redis에 집계
```

#### Redis 데이터 구조

**1. Foreground App Tracking**
```
Key: foreground_app:{subjectId}
Hash:
  - packageName: "com.example.app"
  - startTimestamp: 1728638989686
  - utcOffset: 9
TTL: 24시간
```

**2. Daily Metrics (00:00 기준, utcoffset 고려)**
```
Key: daily_metrics:{subjectId}:{date}
Hash:
  - total_usage_minutes: 총 사용시간
  - dawn_minutes: 새벽 사용시간 (0-6시)
  - morning_minutes: 아침 사용시간 (6-12시)
  - afternoon_minutes: 오후 사용시간 (12-18시)
  - evening_minutes: 저녁 사용시간 (18-24시)
  - dawn_sns_minutes: 새벽 SNS 사용시간
  - daily_pickup_count: 픽업 횟수
  - hour_0 ~ hour_23: 시간별 사용량
  - category_1 ~ category_5: 카테고리별 사용시간
  - app:{packageName}: 앱별 사용시간
TTL: 48시간
```

**3. Realtime Metrics (24시간 슬라이딩 윈도우)**
```
Key: realtime_metrics:{subjectId}:events
Type: Sorted Set
Members: {packageName}:{categoryId}:{usageMinutes}
Score: timestamp (자동 24시간 이전 데이터 삭제)

Key: realtime_metrics:{subjectId}:stats
Hash:
  - top_app_1, top_app_1_minutes
  - top_app_2, top_app_2_minutes
  - top_app_3, top_app_3_minutes
  - game_ratio, social_comm_ratio, learning_ratio, entertainment_ratio
```

#### 주요 기능
1. **시간 계산**
   - FOREGROUND → BACKGROUND 이벤트 쌍으로 실제 사용시간 계산
   - 비정상 데이터 필터링 (음수, 24시간 초과)

2. **시간대별 집계**
   - UTC offset을 고려한 로컬 시간 계산
   - Dawn(0-6), Morning(6-12), Afternoon(12-18), Evening(18-24)

3. **카테고리별 집계**
   - package_name 기반으로 카테고리 판단
   - 새벽 SNS 사용 시간 별도 추적

4. **Top 3 앱 추적**
   - 앱별 사용시간 누적
   - 24시간 실시간 Top 3 계산

5. **픽업 카운트**
   - SCREEN_ON 이벤트로 화면 켜기 횟수 추적

### ✅ 4. 일일 배치 작업 (DailyMetricsBatchService)

#### 실행 시점
- **Cron**: `0 0 0 * * *` (매일 자정)
- **대상**: 어제 날짜의 Redis 데이터

#### 프로세스
```
1. Redis 키 패턴 검색: daily_metrics:*:{yesterday}
   ↓
2. 각 subject_id별로 처리:
   a. DailyUserMetrics 생성 및 저장
      - most_active_hour 계산
      - 카테고리 비율 계산
   b. DailyCategoryUsage 생성 및 저장 (카테고리별)
   c. DailyTopApps 생성 및 저장 (Top 3)
   ↓
3. Redis 데이터 삭제 (메모리 정리)
```

#### 특징
- **부분 실패 허용**: 한 사용자 실패해도 다른 사용자는 정상 처리
- **트랜잭션 독립**: 각 사용자별 독립 처리
- **로깅**: 성공/실패 상세 로그

### ✅ 5. 00시 기준 데이터 집계 (UTC Offset 고려)

#### 구현 방식
```java
// 사용자의 로컬 타임존 적용
ZoneId zoneId = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(utcOffsetHours));
LocalDate localDate = instant.atZone(zoneId).toLocalDate();
```

#### 예시
- 한국 사용자 (UTC+9)
  - UTC 2025-10-10 15:00 → KST 2025-10-11 00:00
  - 2025-10-11 데이터로 집계

- 미국 사용자 (UTC-5)
  - UTC 2025-10-10 05:00 → EST 2025-10-10 00:00
  - 2025-10-10 데이터로 집계

### ✅ 6. 테스트 코드 작성

#### AppUsageTrackerTest
- ✅ FOREGROUND 이벤트 처리
- ✅ BACKGROUND 이벤트 처리 및 시간 계산
- ✅ 비정상 사용시간 필터링 (음수, 24시간 초과)
- ✅ SCREEN_ON 이벤트 처리
- ✅ 자정 경계 케이스 (UTC offset 적용)
- ✅ 24시간 슬라이딩 윈도우

#### CategoryMappingServiceTest
- ✅ CSV 파일 로드 확인
- ✅ 알 수 없는 패키지 → OTHER 카테고리
- ✅ 각 카테고리 매핑 정확도
- ✅ 소셜 앱 판별
- ✅ NULL 처리

#### DailyMetricsBatchServiceTest
- ✅ Redis → DB 저장 프로세스
- ✅ DailyUserMetrics 변환 및 비율 계산
- ✅ DailyCategoryUsage 추출
- ✅ DailyTopApps 추출 및 순위
- ✅ Most Active Hour 계산
- ✅ 부분 실패 허용

## 🔍 시니어 개발자 관점 설계 검토

### ✅ 강점

1. **명확한 책임 분리**
   - AppUsageTracker: 이벤트 처리 및 실시간 집계
   - CategoryMappingService: 카테고리 판단
   - DailyMetricsBatchService: 배치 저장

2. **성능 최적화**
   - Redis Hash/Sorted Set으로 Atomic 연산
   - HashMap 기반 O(1) 카테고리 조회
   - Batch Insert로 DB 저장

3. **데이터 정합성**
   - 비정상 데이터 필터링
   - TTL로 메모리 자동 정리
   - 부분 실패 허용으로 안정성 확보

4. **확장성**
   - Subject ID별 독립 처리
   - Redis 샤딩 가능 구조
   - 배치 작업 병렬화 가능

5. **테스트 가능성**
   - 단위 테스트 작성 완료
   - Mock 기반 독립 테스트
   - Edge case 시나리오 포함

### ⚠️ 개선 고려사항

1. **동시성 제어**
   - 같은 사용자의 동시 이벤트 처리 시 Redis 분산 락 필요

2. **장애 복구**
   - Redis 장애 시 RawData에서 재집계 프로세스 필요

3. **모니터링**
   - 배치 작업 실패 알림 시스템
   - Redis 메모리 사용량 모니터링

4. **성능 벤치마크**
   - 대용량 데이터 처리 성능 테스트 필요

## 📊 데이터 흐름

```
┌─────────────────┐
│  Mobile Client  │
└────────┬────────┘
         │ POST /api/raw-data
         ▼
┌─────────────────┐
│ RawDataController│
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│ RawDataRepository   │──→ TimescaleDB (raw_data)
└─────────────────────┘
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
│ │ Foreground App  │ │ (현재 실행 중인 앱)
│ ├─────────────────┤ │
│ │ Daily Metrics   │ │ (00:00-23:59 집계)
│ ├─────────────────┤ │
│ │ Realtime Stats  │ │ (24시간 윈도우)
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

## 🚀 다음 단계

1. **빌드 및 배포**
   ```bash
   # Gradle Wrapper 생성 (필요 시)
   gradle wrapper

   # 빌드
   ./gradlew clean build

   # Docker 컨테이너로 실행 (docker-compose.yml 사용)
   docker-compose up -d
   ```

2. **통합 테스트**
   - Postman/cURL로 API 테스트
   - Redis 데이터 확인
   - DB 저장 확인

3. **모니터링 설정**
   - Spring Actuator 활성화
   - Prometheus/Grafana 연동
   - 배치 작업 알림 설정

4. **성능 테스트**
   - JMeter/Gatling으로 부하 테스트
   - Redis 메모리 사용량 측정
   - DB 쿼리 성능 최적화

## 📝 주요 파일 목록

### 새로 생성된 파일
```
src/main/java/screentimeai/
├── domain/
│   ├── DailyCategoryUsage.java
│   ├── DailyCategoryUsageId.java
│   ├── DailyCategoryUsageRepository.java
│   ├── DailyTopApps.java
│   ├── DailyTopAppsId.java
│   └── DailyTopAppsRepository.java
├── service/
│   ├── CategoryMappingService.java
│   └── AppUsageTracker.java
└── resources/
    └── all_apps.csv

src/test/java/screentimeai/service/
├── AppUsageTrackerTest.java
├── CategoryMappingServiceTest.java
└── DailyMetricsBatchServiceTest.java

docs/
├── ARCHITECTURE.md
└── IMPLEMENTATION_SUMMARY.md
```

### 수정된 파일
```
src/main/java/screentimeai/service/
├── DailyMetricsAggregator.java (AppUsageTracker 위임)
└── DailyMetricsBatchService.java (DailyCategoryUsage, DailyTopApps 저장 추가)
```

## ✅ 체크리스트

- ✅ RawData 타입 확인
- ✅ MOVE_TO_FOREGROUND/BACKGROUND 로직 구현
- ✅ Redis 실시간 계산 로직 (시간대별, Top 3, 카테고리)
- ✅ 00시 기준 DB 저장 (utcoffset 고려)
- ✅ DailyCategoryUsage 테이블 구현
- ✅ DailyTopApps 테이블 구현
- ✅ package_name 기반 카테고리 매핑
- ✅ 시니어 개발자 관점 설계 검토
- ✅ 24시간 기준 테스트 코드 작성

## 🎯 결론

UsageService의 핵심 기능이 모두 구현되었습니다:

1. **정확한 앱 사용시간 계산**: FOREGROUND/BACKGROUND 이벤트 기반
2. **실시간 메트릭**: Redis 24시간 슬라이딩 윈도우
3. **일일 집계**: 00시 기준 배치 작업으로 DB 저장
4. **카테고리 분류**: 14,445개 앱의 정확한 카테고리 매핑
5. **Top 3 앱 추적**: 일일/실시간 Top 3 앱 관리
6. **UTC Offset 지원**: 사용자별 타임존 고려

모든 코드는 테스트 코드와 함께 작성되었으며, 시니어 개발자 관점에서 확장성과 유지보수성을 고려한 설계입니다.
