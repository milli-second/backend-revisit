# Database Setup Guide

## 🚀 빠른 시작

### 1. Docker Compose 실행
```bash
# backend 디렉토리에서 실행
docker-compose up -d
```

### 2. DB 상태 확인
```bash
docker-compose ps

# 예상 출력:
# NAME                           STATUS
# usageservice-timescaledb       Up
# usageservice-redis             Up
```

### 3. 초기화 스크립트 실행 확인
```bash
# TimescaleDB 로그 확인
docker-compose logs timescaledb | grep "init"

# 성공 시 표시되는 메시지:
# - TimescaleDB extension created
# - Categories table created
# - Raw data hypertable created
# - Daily metrics tables created
```

### 4. DB 접속 (필요시)
```bash
# PostgreSQL/TimescaleDB 접속
docker exec -it usageservice-timescaledb psql -U postgres -d usageservice

# Redis 접속
docker exec -it usageservice-redis redis-cli
```

### 5. 각 서비스 실행
```bash
cd UsageService
./gradlew bootRun
```

---

## 📊 서비스별 스키마

각 서비스의 데이터베이스 스키마는 해당 서비스 폴더의 `DATABASE.md` 참조:
- **UsageService**: `UsageService/DATABASE.md`

---

## 🔍 문제 해결

### 테이블 없음 에러
```bash
docker-compose down -v
docker-compose up -d
```

### Redis 연결 실패
```bash
docker-compose restart redis
```

### 포트 충돌
```bash
lsof -i :5433
# 또는 docker-compose.yml에서 포트 변경
```

---

## 🌐 Docker Compose 종료

```bash
# 컨테이너만 중지
docker-compose down

# 데이터까지 삭제
docker-compose down -v
```
