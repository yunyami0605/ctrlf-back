#!/bin/bash
# Keycloak Service Account 권한 자동 설정 스크립트
# infra-admin 클라이언트의 Service Account에 realm-management 역할을 할당합니다.
set -e
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
REALM="${REALM:-ctrlf}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
REALM_MANAGEMENT_CLIENT="realm-management"
# 필요한 역할 목록
REQUIRED_ROLES=("view-users" "manage-users" "view-realm")
echo ":열쇠와_잠긴_자물쇠: Keycloak Service Account 권한 설정 시작..."
echo "   Keycloak URL: $KEYCLOAK_URL"
echo "   Realm: $REALM"
echo "   Client: $CLIENT_ID"
# Keycloak이 준비될 때까지 대기
echo ":모래가_내려오고_있는_모래시계: Keycloak이 준비될 때까지 대기 중..."
for i in {1..30}; do
    if curl -s -f "$KEYCLOAK_URL/health" > /dev/null 2>&1; then
        echo ":흰색_확인_표시: Keycloak이 준비되었습니다."
        break
    fi
    if [ $i -eq 30 ]; then
        echo ":x: Keycloak이 30초 내에 준비되지 않았습니다."
        exit 1
    fi
    sleep 1
done
# 추가로 realm이 import될 때까지 대기
echo ":모래가_내려오고_있는_모래시계: Realm import 완료 대기 중..."
for i in {1..60}; do
    TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=$ADMIN_USER" \
        -d "password=$ADMIN_PASSWORD" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" | jq -r '.access_token // empty')
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        REALM_EXISTS=$(curl -s -H "Authorization: Bearer $TOKEN" \
            "$KEYCLOAK_URL/admin/realms/$REALM" | jq -r '.realm // empty')
        if [ "$REALM_EXISTS" = "$REALM" ]; then
            echo ":흰색_확인_표시: Realm '$REALM'이 준비되었습니다."
            break
        fi
    fi
    if [ $i -eq 60 ]; then
        echo ":x: Realm '$REALM'이 60초 내에 준비되지 않았습니다."
        exit 1
    fi
    sleep 1
done
# 관리자 토큰 획득
echo ":열쇠: 관리자 토큰 획득 중..."
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASSWORD" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" | jq -r '.access_token')
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    echo ":x: 관리자 토큰 획득 실패"
    exit 1
fi
# infra-admin 클라이언트 ID 조회
echo ":돋보기: '$CLIENT_ID' 클라이언트 조회 중..."
CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" | jq -r '.[0].id // empty')
if [ -z "$CLIENT_UUID" ] || [ "$CLIENT_UUID" = "null" ]; then
    echo ":x: 클라이언트 '$CLIENT_ID'를 찾을 수 없습니다."
    echo ""
    echo ":전구: 해결 방법:"
    echo "   1. Keycloak Admin Console에서 수동으로 클라이언트를 생성하거나"
    echo "   2. Keycloak 볼륨을 삭제하고 재시작하여 realm import를 다시 수행하세요:"
    echo "      docker compose down"
    echo "      docker volume rm ctrlf-back_kc-db-data"
    echo "      docker compose up -d keycloak"
    echo ""
    echo "   클라이언트 설정:"
    echo "   - Client ID: $CLIENT_ID"
    echo "   - Client authentication: ON"
    echo "   - Service accounts roles: ON"
    echo "   - Secret: changeme"
    exit 1
fi
# Service Account 사용자 ID 조회
echo ":상반신_그림자: Service Account 사용자 조회 중..."
SERVICE_ACCOUNT_USER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_UUID/service-account-user" | jq -r '.id // empty')
if [ -z "$SERVICE_ACCOUNT_USER_ID" ] || [ "$SERVICE_ACCOUNT_USER_ID" = "null" ]; then
    echo ":x: Service Account 사용자를 찾을 수 없습니다. 클라이언트의 'Service accounts enabled'가 활성화되어 있는지 확인하세요."
    exit 1
fi
# realm-management 클라이언트 ID 조회
echo ":돋보기: 'realm-management' 클라이언트 조회 중..."
REALM_MGMT_CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$REALM_MANAGEMENT_CLIENT" | jq -r '.[0].id // empty')
if [ -z "$REALM_MGMT_CLIENT_UUID" ] || [ "$REALM_MGMT_CLIENT_UUID" = "null" ]; then
    echo ":x: 'realm-management' 클라이언트를 찾을 수 없습니다."
    exit 1
fi
# realm-management 클라이언트의 역할 목록 조회
echo ":클립보드: 'realm-management' 클라이언트 역할 조회 중..."
ROLES_RESPONSE=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$REALM_MGMT_CLIENT_UUID/roles")
# 각 역할 할당
ASSIGNED_COUNT=0
VERIFIED_COUNT=0
for ROLE_NAME in "${REQUIRED_ROLES[@]}"; do
    echo "   역할 '$ROLE_NAME' 확인 중..."
    # 역할이 존재하는지 확인
    ROLE_EXISTS=$(echo "$ROLES_RESPONSE" | jq -r ".[] | select(.name == \"$ROLE_NAME\") | .id // empty")
    if [ -z "$ROLE_EXISTS" ] || [ "$ROLE_EXISTS" = "null" ]; then
        echo "   :경고:  역할 '$ROLE_NAME'이 존재하지 않습니다. 건너뜁니다."
        continue
    fi
    # 이미 할당되어 있는지 확인
    CURRENT_ROLES=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
        "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/clients/$REALM_MGMT_CLIENT_UUID")
    ALREADY_ASSIGNED=$(echo "$CURRENT_ROLES" | jq -r ".[] | select(.name == \"$ROLE_NAME\") | .id // empty")
    if [ -n "$ALREADY_ASSIGNED" ] && [ "$ALREADY_ASSIGNED" != "null" ]; then
        echo "   :흰색_확인_표시: 역할 '$ROLE_NAME'은 이미 할당되어 있습니다."
        VERIFIED_COUNT=$((VERIFIED_COUNT + 1))
        continue
    fi
    # 역할 할당
    echo "   :두꺼운_더하기_기호: 역할 '$ROLE_NAME' 할당 중..."
    ROLE_JSON=$(echo "$ROLES_RESPONSE" | jq -r ".[] | select(.name == \"$ROLE_NAME\")")
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "[$ROLE_JSON]" \
        "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/clients/$REALM_MGMT_CLIENT_UUID")
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
        echo "   :흰색_확인_표시: 역할 '$ROLE_NAME' 할당 완료"
        ASSIGNED_COUNT=$((ASSIGNED_COUNT + 1))
        VERIFIED_COUNT=$((VERIFIED_COUNT + 1))
    else
        echo "   :x: 역할 '$ROLE_NAME' 할당 실패 (HTTP $HTTP_CODE)"
    fi
done
# roles 클라이언트 스코프 할당 (Default로 추가)
echo ""
echo ":클립보드: 'roles' 클라이언트 스코프 확인 중..."
CLIENT_SCOPES_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET \
    "$KEYCLOAK_URL/admin/realms/$REALM/client-scopes" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json")
HTTP_CODE=$(echo "$CLIENT_SCOPES_RESPONSE" | tail -n1)
CLIENT_SCOPES_BODY=$(echo "$CLIENT_SCOPES_RESPONSE" | sed '$d')
if [ "$HTTP_CODE" = "200" ]; then
    ROLES_SCOPE_ID=$(echo "$CLIENT_SCOPES_BODY" | jq -r '.[] | select(.name == "roles") | .id')
    if [ -n "$ROLES_SCOPE_ID" ] && [ "$ROLES_SCOPE_ID" != "null" ]; then
        # 현재 할당된 Default 클라이언트 스코프 확인
        DEFAULT_SCOPES_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET \
            "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_UUID/default-client-scopes" \
            -H "Authorization: Bearer $ADMIN_TOKEN" \
            -H "Content-Type: application/json")
        DEFAULT_SCOPES_HTTP_CODE=$(echo "$DEFAULT_SCOPES_RESPONSE" | tail -n1)
        DEFAULT_SCOPES_BODY=$(echo "$DEFAULT_SCOPES_RESPONSE" | sed '$d')
        if [ "$DEFAULT_SCOPES_HTTP_CODE" = "200" ]; then
            ROLES_SCOPE_ASSIGNED=$(echo "$DEFAULT_SCOPES_BODY" | jq -r '.[] | select(.name == "roles") | .name')
            if [ -n "$ROLES_SCOPE_ASSIGNED" ]; then
                echo "   :흰색_확인_표시: 'roles' 클라이언트 스코프가 이미 Default로 할당되어 있습니다."
            else
                echo "   :두꺼운_더하기_기호: 'roles' 클라이언트 스코프를 Default로 할당 중..."
                SCOPE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
                    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_UUID/default-client-scopes/$ROLES_SCOPE_ID" \
                    -H "Authorization: Bearer $ADMIN_TOKEN" \
                    -H "Content-Type: application/json")
                SCOPE_HTTP_CODE=$(echo "$SCOPE_RESPONSE" | tail -n1)
                if [ "$SCOPE_HTTP_CODE" = "204" ] || [ "$SCOPE_HTTP_CODE" = "200" ]; then
                    echo "   :흰색_확인_표시: 'roles' 클라이언트 스코프 할당 완료"
                else
                    echo "   :경고:  'roles' 클라이언트 스코프 할당 실패 (HTTP $SCOPE_HTTP_CODE)"
                fi
            fi
        else
            echo "   :경고:  Default 클라이언트 스코프 확인 실패 (HTTP $DEFAULT_SCOPES_HTTP_CODE)"
        fi
    else
        echo "   :경고:  'roles' 클라이언트 스코프를 찾을 수 없습니다."
        echo "      (일반적으로 Keycloak에 기본 제공되므로, 이 메시지는 무시해도 됩니다)"
    fi
else
    echo "   :경고:  클라이언트 스코프 확인 중 오류 발생 (HTTP $HTTP_CODE)"
    echo "      (수동으로 Keycloak Admin Console에서 확인해주세요)"
fi
echo ""
# 모든 역할이 할당되었거나 이미 할당되어 있으면 성공
if [ $VERIFIED_COUNT -eq ${#REQUIRED_ROLES[@]} ]; then
    if [ $ASSIGNED_COUNT -gt 0 ]; then
        echo ":짠: 설정 완료! $ASSIGNED_COUNT개의 역할이 새로 할당되었습니다."
    else
        echo ":짠: 설정 완료! 모든 역할이 이미 할당되어 있습니다."
    fi
    echo ""
    echo ":메모: 할당된 역할:"
    for ROLE_NAME in "${REQUIRED_ROLES[@]}"; do
        echo "   - $ROLE_NAME"
    done
    echo ""
    echo ":흰색_확인_표시: 모든 역할이 정상적으로 설정되었습니다."
    echo ""
    echo ":전구: 권한 확인:"
    echo "   curl http://localhost:9003/admin/users/token/decode | jq"
    exit 0
else
    echo ":x: 역할 할당에 실패했습니다. ($VERIFIED_COUNT/${#REQUIRED_ROLES[@]} 역할 확인됨)"
    echo "   로그를 확인하세요."
    exit 1
fi