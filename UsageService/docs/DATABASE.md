# Database Schema - UsageService

## 📊 데이터베이스 구조

### TimescaleDB (시계열 데이터)

#### raw_data
```sql
CREATE TABLE raw_data (
    subject_id UUID NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    utcoffset INTEGER,
    source_type TEXT,
    source_info TEXT,
    package_name TEXT NOT NULL,
    name TEXT,
    is_system_app INTEGER,
    is_updated_system_app INTEGER,
    type TEXT NOT NULL,  -- MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND 등
    PRIMARY KEY (subject_id, timestamp)
);

SELECT create_hypertable('raw_data', 'timestamp');
```

---

### PostgreSQL (일일 집계)

#### daily_user_metrics
```sql
CREATE TABLE daily_user_metrics (
    subject_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    total_usage_minutes INTEGER,
    dawn_minutes INTEGER,           -- 00-06시
    morning_minutes INTEGER,         -- 06-12시
    afternoon_minutes INTEGER,       -- 12-18시
    evening_minutes INTEGER,         -- 18-24시
    dawn_sns_minutes INTEGER,
    most_active_hour_start INTEGER,
    daily_pickup_count INTEGER,
    game_ratio FLOAT,
    social_comm_ratio FLOAT,
    learning_ratio FLOAT,
    entertainment_ratio FLOAT,
    PRIMARY KEY (subject_id, summary_date)
);
```

#### daily_category_usage
```sql
CREATE TABLE daily_category_usage (
    subject_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    category_id INTEGER NOT NULL,  -- 1:GAME, 2:SOCIAL_COMM, 3:LEARNING, 4:ENTERTAINMENT, 5:OTHER
    usage_minutes INTEGER,
    PRIMARY KEY (subject_id, summary_date, category_id)
);
```

#### daily_top_apps
```sql
CREATE TABLE daily_top_apps (
    subject_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    app_rank INTEGER NOT NULL,     -- 1, 2, 3
    package_name TEXT NOT NULL,
    usage_minutes INTEGER,
    PRIMARY KEY (subject_id, summary_date, app_rank)
);
```

---

## 🗄️ Redis 데이터 구조

### 1. Foreground App (세션 관리)
```
Key: foreground_app:{subjectId}
TTL: 30분

Fields:
  packageName, startTimestamp, utcOffset, lastHeartbeat
```

### 2. Daily Metrics (일일 누적)
```
Key: daily_metrics:{subjectId}:{date}
TTL: 48시간

Fields:
  total_usage_minutes, dawn_minutes, morning_minutes, ...
  hour_0 ~ hour_23, category_1 ~ category_5
  app:{packageName}
```

### 3. Realtime Metrics (24시간 윈도우)
```
Key: realtime_metrics:{subjectId}:events
Type: Sorted Set

Key: realtime_metrics:{subjectId}:stats
Fields: top_app_1, top_app_2, top_app_3, game_ratio, ...
```

---

## 🔄 데이터 흐름

```
POST /api/raw-data
  ↓
TimescaleDB (raw_data 저장)
  ↓
Redis 실시간 집계
  ↓
매일 00:00 Batch Job
  ↓
PostgreSQL (daily_* 테이블 저장)
```

---

## 🚀 초기 설정

### 1. Docker Compose 실행
```bash
cd /Users/layla/IdeaProjects/backend
docker-compose up -d
```

### 2. 테이블 확인
```bash
docker exec -it usageservice-timescaledb psql -U postgres -d usageservice
```

```sql
-- 테이블 목록
\dt

-- 테이블 구조
\d raw_data
\d daily_user_metrics
\d daily_category_usage
\d daily_top_apps
```

### 3. 재초기화 (필요 시)
```bash
docker-compose down -v
docker-compose up -d
```

---

## 📝 카테고리 매핑

**중요**: 앱 카테고리는 DB가 아닌 **메모리(HashMap)**에서 관리

- 파일: `src/main/resources/all_apps.csv` (14,445개)
- 서비스: `CategoryMappingService`
- 메모리: ~1.2 MB
- 성능: O(1)

---

## 🔍 문제 해결

### 테이블 없음 에러
```bash
docker-compose down -v && docker-compose up -d
```

### Redis 연결 실패
```bash
docker-compose ps redis
docker-compose restart redis
```

### 포트 충돌
```bash
# application.yml에서 포트 확인
spring.datasource.url: jdbc:postgresql://localhost:5433/usageservice
```
