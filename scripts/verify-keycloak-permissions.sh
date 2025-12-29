#!/bin/bash

# Keycloak Service Account 권한 확인 스크립트

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
REALM="${REALM:-ctrlf}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
CLIENT_ID="${CLIENT_ID:-infra-admin}"

echo "🔍 Keycloak Service Account 권한 확인 중..."
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
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=realm-management" | jq -r '.[0].id // empty')

if [ -z "$REALM_MGMT_CLIENT_UUID" ] || [ "$REALM_MGMT_CLIENT_UUID" = "null" ]; then
    echo "❌ 'realm-management' 클라이언트를 찾을 수 없습니다."
    exit 1
fi

# 할당된 역할 조회
echo ""
echo "📋 할당된 역할 확인 중..."
ASSIGNED_ROLES=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/clients/$REALM_MGMT_CLIENT_UUID" | jq -r '.[].name // empty')

if [ -z "$ASSIGNED_ROLES" ]; then
    echo "❌ 할당된 역할이 없습니다!"
    exit 1
fi

echo ""
echo "✅ 할당된 역할 목록:"
echo "$ASSIGNED_ROLES" | while read -r role; do
    if [ -n "$role" ]; then
        echo "   - $role"
    fi
done

# 필수 역할 확인
REQUIRED_ROLES=("view-users" "manage-users" "view-realm")
MISSING_ROLES=()

echo ""
echo "🔍 필수 역할 확인 중..."
for ROLE in "${REQUIRED_ROLES[@]}"; do
    if echo "$ASSIGNED_ROLES" | grep -q "^$ROLE$"; then
        echo "   ✅ $ROLE: 할당됨"
    else
        echo "   ❌ $ROLE: 할당되지 않음"
        MISSING_ROLES+=("$ROLE")
    fi
done

echo ""
if [ ${#MISSING_ROLES[@]} -eq 0 ]; then
    echo "🎉 모든 필수 역할이 정상적으로 할당되었습니다!"
    exit 0
else
    echo "⚠️  다음 역할이 할당되지 않았습니다: ${MISSING_ROLES[*]}"
    exit 1
fi

