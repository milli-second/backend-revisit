#!/bin/bash

# Docker 이미지 빌드 스크립트
# K8s 로컬 배포용
# Dockerfile에서 multi-stage build로 빌드하므로 gradle 명령 불필요

set -e  # 에러 발생 시 중단

echo "🐳 Building Docker images for Kubernetes..."

# 스크립트 위치 기준으로 BACKEND_ROOT 자동 설정
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$SCRIPT_DIR"

echo "📂 Backend root: $BACKEND_ROOT"

# UserService (Dockerfile이 빌드도 포함)
echo "📦 Building UserService..."
cd "$BACKEND_ROOT/UserService"
docker build -t userservice:latest .

# UsageService
echo "📦 Building UsageService..."
cd "$BACKEND_ROOT/UsageService"
docker build -t usageservice:latest .

# Gateway
echo "📦 Building Gateway..."
cd "$BACKEND_ROOT/gateway"
docker build -t gateway:latest .

echo "✅ All images built successfully!"
echo ""
echo "📋 Built images:"
docker images | grep -E "userservice|usageservice|gateway" | grep latest

echo ""
echo "🚀 Ready to deploy to Kubernetes!"
echo "   Run: kubectl apply -f k8s/apps/"
