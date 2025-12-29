#!/bin/bash

# Keycloak Service Account 권한 제거 스크립트 (테스트용)
# infra-admin 클라이언트의 Service Account에서 realm-management 역할을 제거합니다.

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
REALM="${REALM:-ctrlf}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"
REALM_MANAGEMENT_CLIENT="realm-management"

# 제거할 역할 목록
ROLES_TO_REMOVE=("view-users" "manage-users" "view-realm")

echo "🗑️  Keycloak Service Account 권한 제거 시작..."
echo "   Keycloak URL: $KEYCLOAK_URL"
echo "   Realm: $REALM"
echo "   Client: $CLIENT_ID"
echo ""

# 관리자 토큰 획득
echo "🔑 관리자 토큰 획득 중..."
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASSWORD" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    echo "❌ 관리자 토큰 획득 실패"
    exit 1
fi

# infra-admin 클라이언트 ID 조회
echo "🔍 '$CLIENT_ID' 클라이언트 조회 중..."
CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" | jq -r '.[0].id // empty')

if [ -z "$CLIENT_UUID" ] || [ "$CLIENT_UUID" = "null" ]; then
    echo "❌ 클라이언트 '$CLIENT_ID'를 찾을 수 없습니다."
    exit 1
fi

# Service Account 사용자 ID 조회
echo "👤 Service Account 사용자 조회 중..."
SERVICE_ACCOUNT_USER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_UUID/service-account-user" | jq -r '.id // empty')

if [ -z "$SERVICE_ACCOUNT_USER_ID" ] || [ "$SERVICE_ACCOUNT_USER_ID" = "null" ]; then
    echo "❌ Service Account 사용자를 찾을 수 없습니다."
    exit 1
fi

# realm-management 클라이언트 ID 조회
echo "🔍 'realm-management' 클라이언트 조회 중..."
REALM_MGMT_CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$REALM_MANAGEMENT_CLIENT" | jq -r '.[0].id // empty')

if [ -z "$REALM_MGMT_CLIENT_UUID" ] || [ "$REALM_MGMT_CLIENT_UUID" = "null" ]; then
    echo "❌ 'realm-management' 클라이언트를 찾을 수 없습니다."
    exit 1
fi

# realm-management 클라이언트의 역할 목록 조회
echo "📋 'realm-management' 클라이언트 역할 조회 중..."
ROLES_RESPONSE=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$REALM_MGMT_CLIENT_UUID/roles")

# 현재 할당된 역할 조회
echo "📋 현재 할당된 역할 확인 중..."
CURRENT_ROLES=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/clients/$REALM_MGMT_CLIENT_UUID")

# 각 역할 제거
REMOVED_COUNT=0
for ROLE_NAME in "${ROLES_TO_REMOVE[@]}"; do
    echo "   역할 '$ROLE_NAME' 확인 중..."
    
    # 역할이 존재하는지 확인
    ROLE_EXISTS=$(echo "$ROLES_RESPONSE" | jq -r ".[] | select(.name == \"$ROLE_NAME\") | .id // empty")
    
    if [ -z "$ROLE_EXISTS" ] || [ "$ROLE_EXISTS" = "null" ]; then
        echo "   ⚠️  역할 '$ROLE_NAME'이 존재하지 않습니다. 건너뜁니다."
        continue
    fi
    
    # 할당되어 있는지 확인
    ASSIGNED_ROLE=$(echo "$CURRENT_ROLES" | jq -r ".[] | select(.name == \"$ROLE_NAME\")")
    
    if [ -z "$ASSIGNED_ROLE" ] || [ "$ASSIGNED_ROLE" = "null" ]; then
        echo "   ℹ️  역할 '$ROLE_NAME'은 이미 할당되지 않았습니다."
        continue
    fi
    
    # 역할 제거
    echo "   ➖ 역할 '$ROLE_NAME' 제거 중..."
    ROLE_JSON=$(echo "$CURRENT_ROLES" | jq -r ".[] | select(.name == \"$ROLE_NAME\")")
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "[$ROLE_JSON]" \
        "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/clients/$REALM_MGMT_CLIENT_UUID")
    
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
        echo "   ✅ 역할 '$ROLE_NAME' 제거 완료"
        REMOVED_COUNT=$((REMOVED_COUNT + 1))
    else
        echo "   ❌ 역할 '$ROLE_NAME' 제거 실패 (HTTP $HTTP_CODE)"
    fi
done

echo ""
if [ $REMOVED_COUNT -gt 0 ]; then
    echo "🎉 제거 완료! $REMOVED_COUNT개의 역할이 제거되었습니다."
    echo ""
    echo "📝 제거된 역할:"
    for ROLE_NAME in "${ROLES_TO_REMOVE[@]}"; do
        echo "   - $ROLE_NAME"
    done
    echo ""
    echo "💡 다시 권한을 설정하려면:"
    echo "   ./scripts/setup-keycloak-service-account.sh"
    exit 0
else
    echo "ℹ️  제거할 역할이 없습니다."
    exit 0
fi

