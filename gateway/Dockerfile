# # 1. 빌드 환경: Gradle을 사용하여 애플리케이션을 빌드합니다.
# FROM gradle:8.5.0-jdk21-jammy AS build
# WORKDIR /home/gradle/project

# # 프로젝트 루트의 build.gradle과 settings.gradle을 복사
# COPY build.gradle ./build.gradle
# COPY settings.gradle ./settings.gradle

# # gateway 모듈 전체를 복사
# COPY gateway/ ./gateway/

# # clean 후 빌드 실행 (이전 빌드 아티팩트 제거)
# RUN gradle :gateway:clean :gateway:build -x test --no-daemon

# # 2. 실행 환경: 빌드된 JAR 파일을 실행합니다.
# FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy
# WORKDIR /app

# # 빌드 환경에서 생성된 JAR 파일을 실행 환경으로 복사합니다.
# COPY --from=build /home/gradle/project/gateway/build/libs/*.jar app.jar

# # 컨테이너가 8088 포트를 사용한다고 외부에 알립니다.
# EXPOSE 8088

# # 시간대 설정
# ENV TZ=Asia/Seoul
# RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# # 컨테이너가 시작될 때 실행될 명령어
# ENTRYPOINT ["java", "-Xmx400M", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]

FROM gradle:8.5.0-jdk21-jammy AS build
WORKDIR /home/gradle/project

COPY build.gradle settings.gradle ./
COPY gateway ./gateway/

RUN gradle :gateway:build -x test --no-daemon

FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /home/gradle/project/gateway/build/libs/*[!plain].jar app.jar

EXPOSE 8088

ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ENTRYPOINT ["java", "-Xmx400M", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
