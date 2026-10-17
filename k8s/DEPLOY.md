# 🚀 Kubernetes 로컬 배포 가이드

## 📋 사전 요구사항

- Docker Desktop with Kubernetes enabled
- kubectl CLI
- 최소 리소스: 4 CPU, 8GB RAM

## 🔧 배포 순서

### 1단계: Docker 이미지 빌드

```bash
# 프로젝트 루트에서
# 모든 서비스 빌드 (5~10분 소요)
./build-images.sh

# 이미지 확인
docker images | grep -E "userservice|usageservice|gateway"
```

### 2단계: Namespace 생성

```bash
kubectl apply -f namespace.yaml

# 확인
kubectl get namespace
```

### 3단계: Secret 생성

저장소에는 값 없는 예시(`secrets/*.example.yaml`)만 있다. 복사해서 값을 채운 뒤 적용한다 (`secrets/*.yaml`은 gitignore 대상).

```bash
cp secrets/db-secrets.example.yaml secrets/db-secrets.yaml    # 값 채우기
cp secrets/app-secrets.example.yaml secrets/app-secrets.yaml  # 값 채우기
kubectl apply -f secrets/db-secrets.yaml -f secrets/app-secrets.yaml

# 확인
kubectl get secrets -n db
kubectl get secrets -n screentimeai
```

### 4단계: 데이터베이스 배포

```bash
# 모든 DB 배포
kubectl apply -f databases/

# Pod 상태 확인 (모두 Running이 될 때까지 2~3분)
kubectl get pods -n db -w

# Ctrl+C로 watch 종료 후 최종 확인
kubectl get pods -n db
kubectl get pvc -n db
```

**예상 출력:**
```
NAME                         READY   STATUS    RESTARTS   AGE
userservice-postgres-0       1/1     Running   0          2m
usageservice-postgres-0      1/1     Running   0          2m
redis-0                      1/1     Running   0          2m
zookeeper-0                  1/1     Running   0          2m
kafka-0                      1/1     Running   0          2m
```

### 5단계: DB 연결 테스트

```bash
# PostgreSQL 접속 테스트
kubectl exec -it userservice-postgres-0 -n db -- psql -U userservice -d userservice_db -c "\dt"
kubectl exec -it usageservice-postgres-0 -n db -- psql -U usageservice -d usageservice_db -c "\\l"

# Redis 접속 테스트
REDIS_PASSWORD=$(kubectl get secret redis-secret -n db -o jsonpath='{.data.REDIS_PASSWORD}' | base64 -d)
kubectl exec -it redis-0 -n db -- redis-cli -a $REDIS_PASSWORD ping

# Kafka 토픽 확인
kubectl exec -it kafka-0 -n db -- kafka-topics --bootstrap-server localhost:9092 --list
```

### 6단계: 애플리케이션 배포

```bash
# 모든 애플리케이션 배포
kubectl apply -f apps/

# Pod 상태 확인 (모두 Running이 될 때까지 1~2분)
kubectl get pods -n screentimeai -w

# Ctrl+C로 종료 후 확인
kubectl get pods -n screentimeai
kubectl get svc -n screentimeai
```

**예상 출력:**
```
NAME                            READY   STATUS    RESTARTS   AGE
userservice-xxxxxxxxxx-xxxxx    1/1     Running   0          1m
usageservice-xxxxxxxxxx-xxxxx   1/1     Running   0          1m
gateway-xxxxxxxxxx-xxxxx        1/1     Running   0          1m
```

### 7단계: 접속 테스트

```bash
# Gateway를 통한 접속 (NodePort 30088)
curl http://localhost:30088/users/test

# Swagger UI 접속
open http://localhost:30088/swagger-ui.html
```

## 🔍 트러블슈팅

### Pod가 CrashLoopBackOff 상태인 경우

```bash
# 로그 확인
kubectl logs -f <pod-name> -n <namespace>

# 예시
kubectl logs -f userservice-xxxxxxxxxx-xxxxx -n screentimeai

# 일반적인 원인:
# 1. DB 연결 실패 → DB Pod가 Running인지 확인
# 2. 환경 변수 오류 → ConfigMap/Secret 확인
# 3. 이미지 없음 → docker images 확인
```

### DB Pod가 Pending 상태인 경우

```bash
# PVC 상태 확인
kubectl get pvc -n db

# 스토리지 클래스 확인
kubectl get storageclass

# Docker Desktop의 경우 자동으로 hostpath provisioner 사용
```

### 애플리케이션이 DB에 연결 못하는 경우

```bash
# DNS 확인
kubectl run -it --rm debug --image=busybox --restart=Never -n screentimeai -- nslookup userservice-postgres.db.svc.cluster.local

# 네트워크 정책 확인 (기본적으로 모든 namespace 간 통신 허용)
kubectl get networkpolicies -A
```

### Redis 비밀번호 오류

```bash
# Secret 확인
kubectl get secret redis-secret -n screentimeai -o yaml

# 비밀번호 확인
kubectl get secret redis-secret -n screentimeai -o jsonpath='{.data.REDIS_PASSWORD}' | base64 -d
```

## 📊 모니터링

### 전체 시스템 상태

```bash
# 모든 Pod 확인
kubectl get pods -A

# 리소스 사용량
kubectl top nodes
kubectl top pods -n db
kubectl top pods -n screentimeai
```

### 로그 실시간 확인

```bash
# Gateway 로그
kubectl logs -f deployment/gateway -n screentimeai

# UserService 로그
kubectl logs -f deployment/userservice -n screentimeai

# UsageService 로그
kubectl logs -f deployment/usageservice -n screentimeai

# DB 로그
kubectl logs -f userservice-postgres-0 -n db
```

### 이벤트 확인

```bash
# namespace별 이벤트
kubectl get events -n screentimeai --sort-by='.lastTimestamp'
kubectl get events -n db --sort-by='.lastTimestamp'
```

## 🧹 전체 삭제

```bash
# 애플리케이션만 삭제
kubectl delete -f k8s/apps/

# DB만 삭제
kubectl delete -f k8s/databases/

# 전체 삭제 (PVC 포함)
kubectl delete namespace screentimeai
kubectl delete namespace db

# PVC는 자동으로 삭제되지만, 확인
kubectl get pvc -A
```

## 🔄 재배포

### 코드 변경 후 재배포

```bash
# 1. 이미지 재빌드
./build-images.sh

# 2. Pod 재시작 (이미지 pull 정책이 IfNotPresent이므로)
kubectl rollout restart deployment/userservice -n screentimeai
kubectl rollout restart deployment/usageservice -n screentimeai
kubectl rollout restart deployment/gateway -n screentimeai

# 3. 상태 확인
kubectl rollout status deployment/userservice -n screentimeai
```

### DB 초기화

```bash
# PVC 삭제 (데이터 완전 삭제)
kubectl delete pvc -n db --all

# DB 재배포
kubectl apply -f k8s/databases/
```

## 📝 주요 엔드포인트

| 서비스 | 로컬 주소 | 설명 |
|--------|----------|------|
| Gateway | http://localhost:30088 | API Gateway |
| Swagger UI | http://localhost:30088/swagger-ui.html | API 문서 |
| UserService (직접) | http://userservice.screentimeai:8080 | 클러스터 내부만 |
| UsageService (직접) | http://usageservice.screentimeai:8080 | 클러스터 내부만 |

## 🎯 다음 단계

- [ ] Istio 설치 및 설정
- [ ] HPA (Horizontal Pod Autoscaler) 설정
- [ ] Prometheus + Grafana 모니터링
- [ ] Ingress 설정 (Istio Gateway)
- [ ] CI/CD 파이프라인 (Jenkins)
