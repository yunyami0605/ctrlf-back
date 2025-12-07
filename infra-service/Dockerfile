FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY build/libs/infra-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
