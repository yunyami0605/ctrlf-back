FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY build/libs/chat-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
