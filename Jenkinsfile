pipeline {
  agent any

  environment {
    REGISTRY = "nexus.cicd.svc.cluster.local:5000"
    NEXUS_REPO_NAME = "screentimeai-repo"
    CREDS = 'nexus-credentials'
    DOCKER_HOST = 'tcp://localhost:2375'
    PATH = "/opt/binaries:${env.PATH}"  // ← Docker, kubectl 추가

    // CI/CD 테스트용 별도 네임스페이스
    APP_NAMESPACE = "screentimeai-cicd-test"
    DB_NAMESPACE = "db-cicd-test"
  }

  stages {
    stage('Checkout') {
      steps {
        echo "📦 Pulling latest code from Git..."
        checkout scm
      }
    }

    stage('Gradle Build') {
      steps {
        echo "⚙️ Running Gradle build..."
        sh './gradlew :UserService:build :InsightService:build :UsageService:build :gateway:build -x test --no-daemon'
      }
    }

    stage('Wait for Docker') {
      steps {
        script {
          echo "⏳ Waiting for Docker daemon..."
          sh '''
            COUNT=0
            MAX_WAIT=30
            while [ $COUNT -lt $MAX_WAIT ]; do
              if docker info > /dev/null 2>&1; then
                echo "✅ Docker daemon is ready!"
                docker version
                break
              fi
              COUNT=$((COUNT + 1))
              echo "Waiting... ($COUNT/$MAX_WAIT)"
              sleep 2
            done
          '''
        }
      }
    }

    stage('Docker Build & Push') {
      steps {
        script {
          def modules = [
            [name: 'userservice', dockerfile: 'UserService/Dockerfile'],
            [name: 'insightservice', dockerfile: 'InsightService/Dockerfile'],
            [name: 'usageservice', dockerfile: 'UsageService/Dockerfile'],
            [name: 'gateway', dockerfile: 'gateway/Dockerfile']
          ]

          withCredentials([usernamePassword(credentialsId: CREDS, usernameVariable: 'U', passwordVariable: 'P')]) {
            sh 'echo "$P" | docker login ${REGISTRY} -u "$U" --password-stdin'

            for (svc in modules) {
              def imagePath = "${REGISTRY}/${svc.name}"
              def imageTag = "${env.BRANCH_NAME}-${env.BUILD_NUMBER}"

              echo "🚀 Building ${svc.name}..."
              sh "docker build -f ${svc.dockerfile} -t ${imagePath}:${imageTag} ."

              echo "📤 Pushing ${imagePath}:${imageTag}..."
              sh "docker push ${imagePath}:${imageTag}"

              env."IMAGE_${svc.name.toUpperCase()}" = "${imagePath}:${imageTag}"
            }

            sh "docker logout ${REGISTRY}"
          }
        }
      }
    }

    stage('Setup Kubernetes Namespaces') {
      steps {
        script {
          echo "🏗️ Setting up CI/CD test namespaces..."

          sh """
            # 네임스페이스 생성
            kubectl create namespace ${DB_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
            kubectl create namespace ${APP_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

            # 라벨 추가
            kubectl label namespace ${DB_NAMESPACE} environment=cicd-test --overwrite
            kubectl label namespace ${APP_NAMESPACE} environment=cicd-test --overwrite

            echo "✅ Namespaces created: ${DB_NAMESPACE}, ${APP_NAMESPACE}"
          """
        }
      }
    }

    stage('Deploy Secrets') {
  steps {
    script {
      echo "🔐 Deploying secrets..."

      sh """
        # DB Secrets을 CI/CD 테스트 네임스페이스에 적용
        if [ -f k8s/secrets/db-secrets.yaml ]; then
          # 정확한 namespace 줄만 치환
          sed 's/^  namespace: db\$/  namespace: ${DB_NAMESPACE}/g' k8s/secrets/db-secrets.yaml | kubectl apply -f -
        fi

        # App Secrets을 CI/CD 테스트 네임스페이스에 적용
        if [ -f k8s/secrets/app-secrets.yaml ]; then
          sed 's/^  namespace: screentimeai\$/  namespace: ${APP_NAMESPACE}/g' k8s/secrets/app-secrets.yaml | kubectl apply -f -
        fi

        echo "✅ Secrets deployed"
      """
    }
  }
}

    stage('Deploy Databases') {
  steps {
    script {
      echo "🗄️ Deploying databases..."

      def databases = [
        'userservice-postgres',
        'usageservice-postgres',
        'redis',
        'kafka'
      ]

      for (db in databases) {
        sh """
          if [ -f k8s/databases/${db}.yaml ]; then
            echo "Deploying ${db}..."
            # 정확한 namespace 줄만 치환
            sed 's/^  namespace: db\$/  namespace: ${DB_NAMESPACE}/g' k8s/databases/${db}.yaml | kubectl apply -f -
          fi
        """
      }

      echo "⏳ Waiting for databases to be ready..."
      sh """
        # PostgreSQL 준비 대기
        kubectl wait --for=condition=ready pod -l app=userservice-postgres -n ${DB_NAMESPACE} --timeout=300s || true
        kubectl wait --for=condition=ready pod -l app=usageservice-postgres -n ${DB_NAMESPACE} --timeout=300s || true

        # Redis 준비 대기
        kubectl wait --for=condition=ready pod -l app=redis -n ${DB_NAMESPACE} --timeout=120s || true

        # Kafka 준비 대기
        kubectl wait --for=condition=ready pod -l app=kafka -n ${DB_NAMESPACE} --timeout=180s || true

        echo "✅ Databases are ready"
      """
    }
  }
}

    stage('Deploy Applications') {
  steps {
    script {
      echo "🚢 Deploying applications with new images..."

      def services = [
        [name: 'userservice', file: 'k8s/apps/userservice.yaml'],
        [name: 'usageservice', file: 'k8s/apps/usageservice.yaml'],
        [name: 'gateway', file: 'k8s/apps/gateway.yaml']
      ]

      for (svc in services) {
        def newImage = env."IMAGE_${svc.name.toUpperCase()}"

        echo "📦 Deploying ${svc.name} with image: ${newImage}"

        sh """
          # YAML 파일에서 namespace와 image 교체
          if [ -f ${svc.file} ]; then
            cat ${svc.file} | \
              sed 's/^  namespace: screentimeai\$/  namespace: ${APP_NAMESPACE}/g' | \
              sed "s|image:.*${svc.name}.*|image: ${newImage}|g" | \
              kubectl apply -f -
          else
            echo "⚠️ ${svc.file} not found, creating deployment..."

            cat <<EOFYAML | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${svc.name}
  namespace: ${APP_NAMESPACE}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${svc.name}
  template:
    metadata:
      labels:
        app: ${svc.name}
    spec:
      containers:
      - name: ${svc.name}
        image: ${newImage}
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "docker"
---
apiVersion: v1
kind: Service
metadata:
  name: ${svc.name}
  namespace: ${APP_NAMESPACE}
spec:
  selector:
    app: ${svc.name}
  ports:
  - port: 8080
    targetPort: 8080
  type: ClusterIP
EOFYAML
          fi
        """
      }

      echo "⏳ Waiting for application rollout..."
      sh """
        kubectl rollout status deployment/userservice -n ${APP_NAMESPACE} --timeout=5m || true
        kubectl rollout status deployment/usageservice -n ${APP_NAMESPACE} --timeout=5m || true
        kubectl rollout status deployment/gateway -n ${APP_NAMESPACE} --timeout=5m || true
      """
    }
  }
}

    stage('Verify Deployment') {
      steps {
        script {
          echo "🔍 Verifying CI/CD test deployment..."

          sh """
            echo "==================================="
            echo "📊 CI/CD Test Environment Status"
            echo "==================================="

            echo "\\n🗄️ Database Namespace: ${DB_NAMESPACE}"
            kubectl get pods -n ${DB_NAMESPACE} -o wide

            echo "\\n🚀 Application Namespace: ${APP_NAMESPACE}"
            kubectl get pods -n ${APP_NAMESPACE} -o wide

            echo "\\n🌐 Services"
            kubectl get svc -n ${APP_NAMESPACE}

            echo "\\n📦 StatefulSets (Databases)"
            kubectl get statefulset -n ${DB_NAMESPACE}

            echo "\\n💾 Persistent Volume Claims"
            kubectl get pvc -n ${DB_NAMESPACE}

            echo "\\n==================================="
            echo "📊 Production Environment (unchanged)"
            echo "==================================="
            kubectl get pods -n screentimeai 2>/dev/null || echo "No production deployment found"
          """
        }
      }
    }
  }

  post {
    success {
      echo "✅ CI/CD Pipeline completed successfully!"
      echo ""
      echo "🎉 Test deployment is running:"
      echo "   - DB Namespace:  ${DB_NAMESPACE}"
      echo "   - App Namespace: ${APP_NAMESPACE}"
      echo ""
      echo "📦 Deployed Images:"
      echo "   - UserService:  ${env.IMAGE_USERSERVICE}"
      echo "   - UsageService: ${env.IMAGE_USAGESERVICE}"
      echo "   - Gateway:      ${env.IMAGE_GATEWAY}"
      echo ""
      echo "ℹ️  Production deployment (screentimeai, db) is unaffected"
      echo ""
      echo "🗑️  To cleanup test deployment:"
      echo "   kubectl delete namespace ${APP_NAMESPACE} ${DB_NAMESPACE}"
    }
    failure {
      echo "❌ Pipeline failed. Check logs for details."
      echo "Production deployment is unaffected."
    }
    always {
      echo ""
      echo "📊 Quick Debug Commands:"
      echo "   kubectl get pods -n ${APP_NAMESPACE}"
      echo "   kubectl get pods -n ${DB_NAMESPACE}"
      echo "   kubectl logs -n ${APP_NAMESPACE} -l app=gateway --tail=50"
    }
  }
}
