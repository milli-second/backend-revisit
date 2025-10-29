#!/bin/bash

# Docker 이미지 빌드 및 푸시 스크립트
# K8s 배포용
# Dockerfile에서 multi-stage build로 빌드하므로 gradle 명령 불필요

set -e  # 에러 발생 시 중단

DOCKER_USERNAME=${1:-""}

if [ -z "$DOCKER_USERNAME" ]; then
    echo "오류: Docker Hub 사용자명을 첫 번째 인자로 전달해야 합니다."
    echo "사용법: ./build-images.sh [DOCKER_USERNAME]"
    exit 1
fi

echo "🐳 Building and pushing Docker images for Kubernetes with -amd64 tag..."
echo "👤 Docker Hub User: $DOCKER_USERNAME"

# 스크립트 위치 기준으로 BACKEND_ROOT 자동 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$SCRIPT_DIR"

# Git commit hash를 태그로 사용 (short hash)
GIT_HASH=$(git rev-parse --short HEAD)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
TAG="${GIT_HASH}-${TIMESTAMP}"

echo "📂 Backend root: $BACKEND_ROOT"
echo "🏷️  Image tag: $TAG"

# 태그 정보를 파일에 저장
cat > "$BACKEND_ROOT/last-build-tag.txt" <<EOF
# Last build information
BUILD_TAG=$TAG
GIT_HASH=$GIT_HASH
TIMESTAMP=$TIMESTAMP
BUILD_TIME=$(date)
DOCKER_USERNAME=$DOCKER_USERNAME
EOF

echo "💾 Tag saved to last-build-tag.txt"

# UserService (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing UserService for AMD64..."
cd "$BACKEND_ROOT/UserService"
docker buildx build --platform linux/amd64 --push \
  -t "$DOCKER_USERNAME/userservice:$TAG" \
  -t "$DOCKER_USERNAME/userservice:latest-amd64" .

# UsageService (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing UsageService for AMD64..."
cd "$BACKEND_ROOT/UsageService"
docker buildx build --platform linux/amd64 --push \
  -t "$DOCKER_USERNAME/usageservice:$TAG" \
  -t "$DOCKER_USERNAME/usageservice:latest-amd64" .

# InsightService (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing InsightService for AMD64..."
cd "$BACKEND_ROOT/InsightService"
docker buildx build --platform linux/amd64 --push \
  -t "$DOCKER_USERNAME/insightservice:$TAG" \
  -t "$DOCKER_USERNAME/insightservice:latest-amd64" .

# Gateway (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing Gateway for AMD64..."
cd "$BACKEND_ROOT/gateway"
docker buildx build --platform linux/amd64 --push \
  -t "$DOCKER_USERNAME/gateway:$TAG" \
  -t "$DOCKER_USERNAME/gateway:latest-amd64" .

echo "✅ All images built and pushed successfully!"
echo ""
echo "📋 Pushed images (with tags: $TAG and latest-amd64):"
echo "  - $DOCKER_USERNAME/userservice:$TAG"
echo "  - $DOCKER_USERNAME/usageservice:$TAG"
echo "  - $DOCKER_USERNAME/insightservice:$TAG"
echo "  - $DOCKER_USERNAME/gateway:$TAG"
echo ""
echo "🚀 Ready to deploy to Kubernetes!"
echo ""
echo "💡 To update k8s deployment with new tag:"
echo "   kubectl set image deployment/userservice userservice=$DOCKER_USERNAME/userservice:$TAG -n screentimeai"
echo "   kubectl set image deployment/usageservice usageservice=$DOCKER_USERNAME/usageservice:$TAG -n screentimeai"
echo "   kubectl set image deployment/insightservice insightservice=$DOCKER_USERNAME/insightservice:$TAG -n screentimeai"
echo "   kubectl set image deployment/gateway gateway=$DOCKER_USERNAME/gateway:$TAG -n screentimeai"
