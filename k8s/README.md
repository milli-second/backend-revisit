# Screen Time AI - Kubernetes Deployment

## 📁 디렉토리 구조

```
k8s/
├── namespace.yaml              # Namespace 정의 (screentimeai, db)
├── secrets/
│   └── db-secrets.yaml        # DB, Redis, Kafka 비밀번호
├── databases/                  # namespace: db
│   ├── userservice-postgres.yaml
│   ├── usageservice-postgres.yaml (TimescaleDB)
│   ├── redis.yaml
│   └── kafka.yaml (+ Zookeeper)
└── apps/                       # namespace: screentimeai
    ├── userservice.yaml
    ├── usageservice.yaml
    ├── insightservice.yaml
    └── gateway.yaml
```

## 🏗️ Namespace 구조

- **`db`**: 모든 데이터베이스 (PostgreSQL, Redis, Kafka, Zookeeper)
- **`screentimeai`**: 애플리케이션 서비스 (UserService, UsageService, Gateway)

**장점:**
- DB와 애플리케이션 분리로 관리 용이
- RBAC 권한 분리 (DB 접근 제한 가능)
- 리소스 쿼터를 각각 설정 가능

## 🔐 Secret 관리 방식

### 선택된 방식: Kubernetes Secret
- **장점**:
  - K8s 네이티브, 추가 도구 불필요
  - etcd에 자동 암호화 저장 (encryption at rest 활성화 시)
  - RBAC로 접근 제어 가능
  - 간단한 사용법

### 대안 (프로덕션 환경):
1. **Sealed Secrets**: Git에 암호화된 Secret 커밋 가능
2. **External Secrets Operator**: AWS Secrets Manager, Vault 연동
3. **HashiCorp Vault**: 중앙 집중식 Secret 관리

## 🚀 배포 순서

### 1. Namespace 생성
```bash
kubectl apply -f namespace.yaml
```

### 2. Secret 생성
```bash
# ⚠️ 프로덕션에서는 비밀번호를 반드시 변경하세요!
kubectl apply -f secrets/db-secrets.yaml
kubectl apply -f ./secrets/app-secrets.yaml
```

### 3. 데이터베이스 배포
```bash
# 순서대로 배포 (의존성 고려)
kubectl apply -f databases/userservice-postgres.yaml
kubectl apply -f databases/usageservice-postgres.yaml
kubectl apply -f databases/redis.yaml
kubectl apply -f databases/kafka.yaml

# 상태 확인
kubectl get statefulset -n db
kubectl get pvc -n db
```

### 4. 데이터베이스 준비 대기
```bash
# 모든 Pod가 Running 상태가 될 때까지 대기
kubectl wait --for=condition=ready pod -l app=userservice-postgres -n db --timeout=120s
kubectl wait --for=condition=ready pod -l app=usageservice-postgres -n db --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n db --timeout=120s
kubectl wait --for=condition=ready pod -l app=kafka -n db --timeout=120s
```

### 5. 애플리케이션 배포
```bash
kubectl apply -f apps/userservice.yaml
kubectl apply -f apps/usageservice.yaml
kubectl apply -f apps/insightservice.yaml
kubectl apply -f apps/gateway.yaml
```

## 🔍 모니터링

### Pod 상태 확인
```bash
# 모든 namespace
kubectl get pods -A

# DB namespace
kubectl get pods -n db -w

# 애플리케이션 namespace
kubectl get pods -n screentimeai -w
```

### StatefulSet 확인
```bash
kubectl get statefulset -n db
```

### PVC (영구 볼륨) 확인
```bash
kubectl get pvc -n db
```

### 로그 확인
```bash
# PostgreSQL
kubectl logs -f userservice-postgres-0 -n db
kubectl logs -f usageservice-postgres-0 -n db

# Redis
kubectl logs -f redis-0 -n db

# Kafka
kubectl logs -f kafka-0 -n db
```

### Secret 확인
```bash
# Secret 목록
kubectl get secrets -n db

# Secret 내용 확인 (base64 디코딩)
kubectl get secret userservice-db-secret -n db -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d
```

## 🗄️ 데이터베이스 접속

### UserService PostgreSQL
```bash
kubectl exec -it userservice-postgres-0 -n db -- psql -U userservice -d userservice_db
```

### UsageService PostgreSQL (TimescaleDB)
```bash
kubectl exec -it usageservice-postgres-0 -n db -- psql -U usageservice -d usageservice_db
```

### Redis
```bash
# 비밀번호 확인
REDIS_PASSWORD=$(kubectl get secret redis-secret -n db -o jsonpath='{.data.REDIS_PASSWORD}' | base64 -d)

# Redis CLI 접속
kubectl exec -it redis-0 -n db -- redis-cli -a $REDIS_PASSWORD
```

### Kafka
```bash
# Kafka 토픽 확인
kubectl exec -it kafka-0 -n db -- kafka-topics --bootstrap-server localhost:9092 --list
```

## 🔄 스케일링

### StatefulSet 스케일 조정
```bash
# Kafka를 3개 노드로 확장 (프로덕션 권장)
kubectl scale statefulset kafka -n db --replicas=3

# 상태 확인
kubectl get pods -n db -l app=kafka
```

## 🧹 삭제

### 전체 삭제
```bash
kubectl delete namespace screentimeai
kubectl delete namespace db
```

### 개별 삭제 (데이터 보존 원할 때)
```bash
# 애플리케이션만 삭제
kubectl delete -f apps/

# 데이터베이스만 삭제
kubectl delete -f databases/

# ⚠️ PVC는 수동으로 삭제해야 데이터가 완전히 삭제됨
kubectl delete pvc -n db --all
```

## 📊 리소스 요구사항

### 데이터베이스 (namespace: db)

| 컴포넌트 | CPU Request | CPU Limit | Memory Request | Memory Limit | Storage |
|---------|-------------|-----------|----------------|--------------|---------|
| UserService DB | 100m | 200m | 128Mi | 256Mi | 5Gi |
| UsageService DB | 200m | 400m | 256Mi | 512Mi | 10Gi |
| Redis | 100m | 200m | 128Mi | 256Mi | 3Gi |
| Zookeeper | 100m | 200m | 128Mi | 256Mi | 2Gi |
| Kafka | 200m | 400m | 256Mi | 512Mi | 5Gi |

**DB 총합**: 0.7 vCPU (request), 1.4 vCPU (limit), 896Mi Memory, 25Gi Storage

### 애플리케이션 (namespace: screentimeai) - 예상

| 컴포넌트 | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---------|-------------|-----------|----------------|--------------|
| UserService | 200m | 500m | 256Mi | 512Mi |
| UsageService | 300m | 700m | 384Mi | 768Mi |
| Gateway | 100m | 300m | 128Mi | 256Mi |

**앱 총합**: 0.6 vCPU (request), 1.5 vCPU (limit), 768Mi Memory

---

**전체 시스템 총합**:
- **CPU**: 1.3 vCPU (request) / 2.9 vCPU (limit)
- **Memory**: 1.7Gi (request) / 3.2Gi (limit)
- **Storage**: 25Gi (DB만)

⚠️ **주의**: 공모전 제한 (vCPU: 2, Memory: 4GB, Storage: 50~100GB)에 맞춰 경량화됨

## 🔒 프로덕션 체크리스트

- [ ] Secret의 모든 비밀번호 변경
- [ ] `storageClassName` 설정 (클러스터에 맞게)
- [ ] etcd encryption at rest 활성화
- [ ] RBAC 정책 설정
- [ ] Network Policy 적용
- [ ] 백업 정책 수립
- [ ] 모니터링 (Prometheus, Grafana) 설정
- [ ] Ingress/LoadBalancer 설정
- [ ] TLS/SSL 인증서 적용
- [ ] Resource Quota 설정
