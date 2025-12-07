docker exec -it platform-postgres psql -U postgres -d db

# 실행
./gradlew :chat-service:bootRun
./gradlew :education-service:bootRun
./gradlew :infra-service:bootRun
./gradlew :chat-service:bootRun
 
# DB (Docker Compose)

docker compose up -d postgres
docker compose ps
docker compose logs -f postgres
docker compose down

Chat: http://localhost:9001/swagger-ui.html (OpenAPI: http://localhost:9001/v3/api-docs)
Education: http://localhost:9002/swagger-ui.html (OpenAPI: http://localhost:9002/v3/api-docs)
Infra: http://localhost:9003/swagger-ui.html (OpenAPI: http://localhost:9003/v3/api-docs)
Quiz: http://localhost:9004/swagger-ui.html (OpenAPI: http://localhost:9004/v3/api-docs)