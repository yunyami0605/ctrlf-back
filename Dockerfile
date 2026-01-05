FROM quay.io/keycloak/keycloak:24.0

# 1. 리얼름 설정 파일 복사
COPY ./keycloak-realms /opt/keycloak/data/import/

# 2. 커스텀 테마 복사
COPY ./keycloak-themes/ctrlf-theme /opt/keycloak/themes/ctrlf-theme

# 3. 환경 변수 설정 (기본 최적화)
ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_ENABLED=true

# 4. 실행 명령 (임포트 활성화)
ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--import-realm"]