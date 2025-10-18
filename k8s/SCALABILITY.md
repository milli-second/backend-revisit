# 확장성 (Scalability) 전략

## 🎯 현재 구성 (공모전 리소스 제한)

- **vCPU**: 2 cores
- **Memory**: 4GB
- **Storage**: 50GB
- **예상 처리량**: 10~20 RPS, 50 동시 사용자

## 📈 스케일링 전략

### 1. 수평 확장 (Horizontal Scaling)

#### Pod Replica 증가
```yaml
# 리소스가 2배 증가하면
UserService: 1 → 3 replicas
UsageService: 1 → 3 replicas
Gateway: 1 → 2 replicas

예상 처리량: 20 RPS → 60 RPS
```

#### StatefulSet 확장
```yaml
# DB도 확장 가능
PostgreSQL: Read Replica 추가
Redis: Cluster mode (3 master, 3 replica)
Kafka: 1 broker → 3 brokers
```

### 2. 수직 확장 (Vertical Scaling)

| 리소스 배수 | vCPU | Memory | 예상 RPS | 동시 사용자 |
|------------|------|--------|----------|-------------|
| 1x (현재) | 2 | 4GB | 20 | 50 |
| 2x | 4 | 8GB | 50 | 200 |
| 5x | 10 | 20GB | 150 | 500 |
| 10x | 20 | 40GB | 300+ | 1,000+ |

### 3. 데이터 파티셔닝

#### TimescaleDB 자동 파티셔닝
```sql
-- 일별 파티션 (자동)
SELECT create_hypertable('raw_data', 'timestamp');
SELECT add_retention_policy('raw_data', INTERVAL '30 days');
```

#### Kafka Topic Partitioning
```bash
# 파티션 증가 → 병렬 처리 증가
kafka-topics --create --topic screen-events \
  --partitions 10 \
  --replication-factor 3
```

### 4. 캐싱 전략으로 DB 부하 감소

```
요청 → Redis (캐시 히트 80%) → DB (20%만 도달)

효과:
- DB 부하 80% 감소
- 응답 속도 10배 향상 (10ms vs 100ms)
- 같은 리소스로 5배 더 많은 트래픽 처리
```

### 5. 비동기 처리 (Kafka)

```
동기 처리: 1 요청 = 100ms 처리 → 10 RPS
비동기 처리: 1 요청 = 10ms 응답 → 100 RPS

Kafka가 백그라운드 처리 → 사용자는 즉시 응답
```

## 🧪 부하 테스트 결과 (예상)

### 테스트 시나리오
```bash
# k6 부하 테스트
k6 run --vus 50 --duration 1m load-test.js
```

### 현재 설정 (2 vCPU, 4GB)
- **평균 응답 시간**: 50ms
- **P95 응답 시간**: 200ms
- **최대 RPS**: 20
- **에러율**: < 1%

### 예상 확장 결과

| vCPU | Memory | Replicas | RPS | P95 지연 | 비용 배수 |
|------|--------|----------|-----|----------|-----------|
| 2 | 4GB | 1 | 20 | 200ms | 1x |
| 4 | 8GB | 2 | 50 | 150ms | 2x |
| 8 | 16GB | 4 | 120 | 100ms | 4x |
| 16 | 32GB | 8 | 300 | 80ms | 8x |

## 🏗️ 아키텍처 확장성 포인트

### ✅ 확장 가능한 설계
1. **Stateless 애플리케이션**: 어디서든 실행 가능
2. **서비스 독립성**: UserService, UsageService 별도 확장
3. **데이터베이스 분리**: 서비스별 DB → 병목 분산
4. **메시지 큐**: Kafka로 비동기 처리
5. **캐싱 레이어**: Redis로 읽기 부하 감소

### ✅ Kubernetes 네이티브 기능
1. **HPA**: CPU 기반 자동 확장
2. **VPA**: Memory 자동 조정
3. **PDB**: 안전한 롤링 업데이트
4. **Service Mesh (Istio)**: 트래픽 분산, 서킷 브레이커

## 🚨 병목 지점 및 해결책

### 1. 데이터베이스 병목
- **문제**: DB가 먼저 한계 도달
- **해결**: Read Replica, Connection Pooling, 캐싱

### 2. 스토리지 병목
- **문제**: 25GB는 1~2주치 데이터만 저장
- **해결**: S3/Object Storage로 Cold Data 이동

### 3. 네트워크 병목
- **문제**: 단일 Ingress에 트래픽 집중
- **해결**: CDN, 다중 Ingress, Geo-distribution

## 💰 비용 대비 성능

### 리소스 효율성
```
현재: 2 vCPU, 4GB → 20 RPS
단순 확장: 20 vCPU, 40GB → 200 RPS (10배 비용)

최적화 후:
- Redis 캐싱: 5배 효율 증가
- Kafka 비동기: 2배 효율 증가
- Connection Pool: 1.5배 효율 증가

결과: 4 vCPU, 8GB → 150 RPS (2배 비용으로 7.5배 성능)
```

## 📚 대규모 트래픽 대응 로드맵

### Phase 1: 현재 (공모전)
- 리소스: 2 vCPU, 4GB
- 목표: 아키텍처 검증, 데모

### Phase 2: 베타 출시 (100 사용자)
- 리소스: 4 vCPU, 8GB
- 추가: HPA, Redis Cluster

### Phase 3: 정식 출시 (1,000 사용자)
- 리소스: 16 vCPU, 32GB
- 추가: DB Read Replica, Kafka 3 brokers

### Phase 4: 성장기 (10,000 사용자)
- 리소스: 50+ vCPU, 100+ GB
- 추가: Multi-region, CDN, Object Storage

## 🎓 결론

**현재 설정은 대규모 트래픽을 처리할 수 없지만:**

1. ✅ 아키텍처는 확장 가능하게 설계됨
2. ✅ Kubernetes를 통한 자동 확장 지원
3. ✅ 병목 지점을 파악하고 해결 방안 보유
4. ✅ 리소스 효율적인 최적화 전략 적용
5. ✅ 단계적 확장 로드맵 수립

→ **"지금은 작지만, 언제든 크게 확장 가능"**

공모전에서는 **현재 구현보다 확장 가능성**을 평가합니다.
