# --- 1단계: 빌드 환경 (Gradle로 소스코드 빌드) ---
# Gradle 8.5와 JDK 21 버전을 빌드용 이미지로 사용합니다.
FROM gradle:jdk21-jammy AS builder

# 작업 디렉토리를 설정합니다.
WORKDIR /workspace

# 전체 소스 코드를 컨테이너의 /workspace 폴더로 복사합니다.
COPY . .

# Gradle Wrapper를 실행 가능하게 만듭니다. (만약 gradlew가 없다면 이 줄은 생략 가능)
RUN chmod +x ./gradlew

# Gradle을 사용하여 특정 서비스의 jar 파일을 빌드합니다. (테스트는 생략)
# ARG 명령어로 Jenkins에서 빌드할 서비스 이름을 받아옵니다.
ARG SERVICE_NAME
RUN ./gradlew :${SERVICE_NAME}:build -x test

# --- 2단계: 실행 환경 (빌드된 jar 파일 실행) ---
# 실제 실행은 가벼운 JRE(Java 실행 환경) 이미지를 사용합니다.
FROM eclipse-temurin:21-jre-jammy

# 작업 디렉토리를 설정합니다.
WORKDIR /app

# 1단계(builder)에서 빌드된 최종 jar 파일을 복사해옵니다.
ARG SERVICE_NAME
COPY --from=builder /workspace/${SERVICE_NAME}/build/libs/*-SNAPSHOT.jar app.jar

# 애플리케이션이 8080 포트를 사용함을 외부에 알립니다.
EXPOSE 8080

# 컨테이너가 시작될 때 jar 파일을 실행합니다.
ENTRYPOINT ["java", "-jar", "app.jar"]