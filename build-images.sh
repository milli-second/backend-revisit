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

echo "📂 Backend root: $BACKEND_ROOT"

# UserService (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing UserService for AMD64..."
cd "$BACKEND_ROOT/UserService"
docker buildx build --platform linux/amd64 --push -t "$DOCKER_USERNAME/userservice:latest-amd64" .

# UsageService (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing UsageService for AMD64..."
cd "$BACKEND_ROOT/UsageService"
docker buildx build --platform linux/amd64 --push -t "$DOCKER_USERNAME/usageservice:latest-amd64" .

# Gateway (AMD64용으로 빌드 및 푸시)
echo "📦 Building and pushing Gateway for AMD64..."
cd "$BACKEND_ROOT/gateway"
docker buildx build --platform linux/amd64 --push -t "$DOCKER_USERNAME/gateway:latest-amd64" .

echo "✅ All images built and pushed successfully!"
echo ""
echo "📋 Pushed images:"
echo "  - $DOCKER_USERNAME/userservice:latest-amd64"
echo "  - $DOCKER_USERNAME/usageservice:latest-amd64"
echo "  - $DOCKER_USERNAME/gateway:latest-amd64"
echo ""
echo "🚀 Ready to deploy to Kubernetes!"
