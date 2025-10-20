#!/bin/bash

# 서버에서 실행: 전체 재배포 스크립트
# 사용법: bash redeploy.sh

set -e

echo "🔄 전체 재배포 시작..."

# 1. 애플리케이션 삭제
echo "1. 애플리케이션 삭제..."
sudo kubectl delete deployment --all -n screentimeai || true
sudo kubectl delete configmap --all -n screentimeai || true

# 2. DB 삭제 (Secret 제외)
echo "2. DB 삭제..."
sudo kubectl delete statefulset --all -n db || true
sudo kubectl delete deployment --all -n db || true
sudo kubectl delete service --all -n db || true
sudo kubectl delete configmap --all -n db || true

sleep 5

# 3. Secrets 재생성
echo "3. Secrets 확인/생성..."
sudo kubectl apply -f k8s/secrets/

# 4. DB 배포
echo "4. DB 배포..."
sudo kubectl apply -f k8s/databases-simple/

echo "   DB 준비 대기 중..."
sleep 60

# 5. 애플리케이션 배포
echo "5. 애플리케이션 배포..."
sudo kubectl apply -f k8s/apps/

echo "   애플리케이션 시작 대기 중..."
sleep 30

# 6. 상태 확인
echo ""
echo "========================================="
echo "📊 배포 상태"
echo "========================================="
echo ""
echo "DB Pods:"
sudo kubectl get pods -n db
echo ""
echo "Application Pods:"
sudo kubectl get pods -n screentimeai
echo ""
echo "Services:"
sudo kubectl get svc -n screentimeai
