# 1. Build stage (선택 사항: 이미 빌드된 JAR가 있다면 2단계만 사용)
FROM amazoncorretto:17-alpine-jdk AS build
WORKDIR /app
COPY . .
RUN gradlew :infra-service:build -x test

# 2. Run stage
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app
# 빌드된 jar 파일 이름을 확인하여 복사하세요
COPY infra-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
