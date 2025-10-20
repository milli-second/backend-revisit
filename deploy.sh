#!/bin/bash

# KT Cloud 자동 배포 스크립트
# 사용법: ./deploy.sh [DOCKER_USERNAME] [SERVER_IP] [SSH_KEY_PATH]

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
    echo -e "${RED}사용법: ./deploy.sh [DOCKER_USERNAME] [SERVER_IP] [SSH_KEY_PATH]${NC}"
    echo "예: ./deploy.sh myusername 123.456.789.0 ~/.ssh/ktcloud.pem"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🚀 KT Cloud 자동 배포${NC}"
echo -e "${GREEN}========================================${NC}"
echo "Docker Hub: $DOCKER_USERNAME"
echo "서버 IP: $SERVER_IP"
echo ""

# 1. Docker 이미지 빌드 & 푸시
echo -e "${YELLOW}[1/4] Docker 이미지 빌드 & 푸시...${NC}"
./build-images.sh $DOCKER_USERNAME

echo -e "${GREEN}✓ 이미지 푸시 완료${NC}"

# 2. K8s YAML 파일 업데이트
echo -e "${YELLOW}[2/4] K8s YAML 업데이트...${NC}"
./update-k8s-images.sh $DOCKER_USERNAME
echo -e "${GREEN}✓ YAML 업데이트 완료${NC}"

# 3. 파일 전송
echo -e "${YELLOW}[3/4] 배포 파일 서버 전송...${NC}"
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" "mkdir -p ~/screentimeai/k8s"
scp -i "$SSH_KEY" -r k8s/* "$SERVER_USER@$SERVER_IP:~/screentimeai/k8s/"
echo -e "${GREEN}✓ 파일 전송 완료${NC}"

# 4. 서버에서 배포 실행
echo -e "${YELLOW}[4/4] 서버에서 배포 실행...${NC}"
ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" bash << 'ENDSSH'
set -e

# K3s 설치
if ! command -v k3s &> /dev/null; then
    echo "K3s 설치 중..."
    curl -sfL https://get.k3s.io | sh -
    sudo chmod 644 /etc/rancher/k3s/k3s.yaml
fi

cd ~/screentimeai

# 배포 (sudo kubectl 사용)
echo "StorageClass 생성..."
sudo kubectl apply -f k8s/storage/ || echo "StorageClass 이미 존재하거나 스킵됨"

echo "네임스페이스 생성..."
sudo kubectl apply -f k8s/namespace.yaml

echo "Secrets 생성..."
sudo kubectl apply -f
k8s/secrets/

echo "데이터베이스 배포..."
sudo kubectl apply -f k8s/databases/

echo "DB 준비 대기 중..."
sleep 60

echo "애플리케이션 배포..."
sudo kubectl apply -f k8s/apps/

echo ""
echo "배포 상태:"
sudo kubectl get pods -n screentimeai
sudo kubectl get pods -n db
sudo kubectl get svc -n screentimeai
ENDSSH

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ 배포 완료!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Gateway 접속: http://$SERVER_IP:30088"
echo ""
echo "테스트:"
echo "  curl http://$SERVER_IP:30088/actuator/health"
