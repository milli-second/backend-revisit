#!/bin/bash

# K8s YAML 파일의 이미지 경로를 Docker Hub 경로로 업데이트
# 사용법: ./update-k8s-images.sh [DOCKER_USERNAME]

set -e

DOCKER_USERNAME=${1:-""}

if [ -z "$DOCKER_USERNAME" ]; then
    echo "사용법: ./update-k8s-images.sh [DOCKER_USERNAME]"
    echo "예: ./update-k8s-images.sh myusername"
    exit 1
fi

echo "🔧 K8s YAML 파일 업데이트 중..."
echo "Docker Hub 사용자명: $DOCKER_USERNAME"
echo ""

# userservice.yaml 업데이트
sed -i.bak "s|image: .*userservice:.*|image: $DOCKER_USERNAME/userservice:latest-amd64|g" k8s/apps/userservice.yaml
sed -i.bak "s|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g" k8s/apps/userservice.yaml

# usageservice.yaml 업데이트
sed -i.bak "s|image: .*usageservice:.*|image: $DOCKER_USERNAME/usageservice:latest-amd64|g" k8s/apps/usageservice.yaml
sed -i.bak "s|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g" k8s/apps/usageservice.yaml

# gateway.yaml 업데이트
sed -i.bak "s|image: .*gateway:.*|image: $DOCKER_USERNAME/gateway:latest-amd64|g" k8s/apps/gateway.yaml
sed -i.bak "s|imagePullPolicy: IfNotPresent|imagePullPolicy: Always|g" k8s/apps/gateway.yaml

# 백업 파일 삭제
rm -f k8s/apps/*.bak

echo "✅ 이미지 경로가 업데이트되었습니다!"
echo ""
echo "업데이트된 이미지:"
echo "  - $DOCKER_USERNAME/userservice:latest-amd64"
echo "  - $DOCKER_USERNAME/usageservice:latest-amd64"
echo "  - $DOCKER_USERNAME/gateway:latest-amd64"