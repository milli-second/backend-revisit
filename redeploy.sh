#!/bin/bash

# KT Cloud 원격 서버 전체 재배포 스크립트
# 사용법: ./redeploy.sh [DOCKER_USERNAME] [SERVER_IP] [SSH_KEY_PATH]

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

DOCKER_USERNAME=${1:-""}
SERVER_IP=${2:-""}
SSH_KEY=${3:-"~/.ssh/id_rsa"}
SERVER_USER="ubuntu"

if [ -z "$DOCKER_USERNAME" ] || [ -z "$SERVER_IP" ]; then
    echo -e "${RED}사용법: ./redeploy.sh [DOCKER_USERNAME] [SERVER_IP] [SSH_KEY_PATH]${NC}"
    echo "예: ./redeploy.sh myusername 123.456.789.0 ~/.ssh/ktcloud.pem"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🚀 KT Cloud 원격 서버 전체 재배포${NC}"
echo -e "${GREEN}========================================${NC}"

# 1. K8s YAML 파일 업데이트 (이미지 태그, 서버 주소 등)
echo -e "${YELLOW}[1/3] K8s YAML 파일 업데이트...${NC}"
./update-k8s-images.sh $DOCKER_USERNAME
sed -i.bak "s|__SERVER_IP__|$SERVER_IP|g" k8s/apps/userservice.yaml
sed -i.bak "s|__SERVER_IP__|$SERVER_IP|g" k8s/apps/usageservice.yaml
rm -f k8s/apps/*.bak
echo -e "${GREEN}✓ YAML 업데이트 완료${NC}"

# 2. 파일 전송
echo -e "${YELLOW}[2/3] 배포 파일 서버 전송...${NC}"
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" "mkdir -p ~/screentimeai/k8s ~/screentimeai/gateway-k8s"
scp -i "$SSH_KEY" -r k8s/* "$SERVER_USER@$SERVER_IP:~/screentimeai/k8s/"
scp -i "$SSH_KEY" -r gateway/kubernetes/* "$SERVER_USER@$SERVER_IP:~/screentimeai/gateway-k8s/"
echo -e "${GREEN}✓ 파일 전송 완료${NC}"

# 3. 서버에서 재배포 실행
echo -e "${YELLOW}[3/3] 서버에서 재배포 스크립트 실행...${NC}"
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" bash << 'ENDSSH'
set -e

cd ~/screentimeai

echo "🔄 전체 재배포 시작..."

# 1. Istio 설정 삭제
echo "1. Istio 설정 삭제..."
sudo kubectl delete gateway --all -n default || true
sudo kubectl delete virtualservice --all -n default || true
sudo kubectl delete certificate --all -n default || true
sudo kubectl delete clusterissuer --all || true

# 2. 애플리케이션 삭제
echo "2. 애플리케이션 삭제..."
sudo kubectl delete deployment --all -n screentimeai || true
sudo kubectl delete service --all -n screentimeai || true
sudo kubectl delete configmap --all -n screentimeai || true

# 3. DB 삭제 (Secret 제외)
echo "3. DB 삭제..."
sudo kubectl delete statefulset --all -n db || true
sudo kubectl delete deployment --all -n db || true
sudo kubectl delete service --all -n db || true
sudo kubectl delete configmap --all -n db || true

sleep 10

# 4. Secrets 확인/생성
echo "4. Secrets 확인/생성..."
sudo kubectl apply -f k8s/secrets/

# 5. DB 배포
echo "5. DB 배포..."
sudo kubectl apply -f k8s/databases-simple/

echo "   DB 준비 대기 중..."
sleep 60

# 6. 애플리케이션 배포
echo "6. 애플리케이션 배포..."
sudo kubectl apply -f k8s/apps/

# 7. Istio 및 인증서 설정 적용
echo "7. Istio 및 인증서 설정 적용..."
sudo kubectl apply -f gateway-k8s/cluster-issuer.yaml
sudo kubectl apply -f gateway-k8s/certificate.yaml
sudo kubectl apply -f gateway-k8s/gateway.yaml
sudo kubectl apply -f gateway-k8s/virtual-service.yaml

echo "   배포 완료 대기 중..."
sleep 30

# 8. 상태 확인
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
echo ""
echo "Istio Gateway:"
sudo kubectl get gateway -n default

ENDSSH

echo -e "${GREEN}✅ 원격 서버 재배포 완료!${NC}"