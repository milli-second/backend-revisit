#!/bin/bash

# K8s 디버깅 스크립트
# 사용법: ./debug-k8s.sh [SERVER_IP] [SSH_KEY_PATH]

SERVER_IP=${1:-""}
SSH_KEY=${2:-"~/.ssh/id_rsa"}
SERVER_USER="ubuntu"

if [ -z "$SERVER_IP" ]; then
    echo "사용법: ./debug-k8s.sh [SERVER_IP] [SSH_KEY_PATH]"
    exit 1
fi

echo "🔍 K8s 상태 디버깅..."
echo ""

ssh -i "$SSH_KEY" "$SERVER_USER@$SERVER_IP" bash << 'ENDSSH'
echo "1. StorageClass 확인:"
sudo kubectl get storageclass

echo ""
echo "2. PVC 상태 확인:"
sudo kubectl get pvc -n db

echo ""
echo "3. Pending Pod 상세 정보 (첫 번째 Pod):"
POD_NAME=$(sudo kubectl get pods -n db -o jsonpath='{.items[0].metadata.name}')
sudo kubectl describe pod $POD_NAME -n db | tail -20

echo ""
echo "4. 노드 상태:"
sudo kubectl get nodes

echo ""
echo "5. 이벤트 확인:"
sudo kubectl get events -n db --sort-by='.lastTimestamp' | tail -10
ENDSSH
