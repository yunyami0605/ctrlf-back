#!/bin/bash

# keycloak-init 상태 확인 스크립트

echo "=== 1. keycloak-init 컨테이너 상태 확인 ==="
docker compose ps keycloak-init 2>/dev/null || echo "컨테이너를 찾을 수 없습니다."

echo -e "\n=== 2. keycloak-init 로그 (마지막 30줄) ==="
docker compose logs --tail=30 keycloak-init 2>/dev/null || echo "로그를 찾을 수 없습니다."

echo -e "\n=== 3. keycloak-init Exit Code ==="
STATUS=$(docker compose ps keycloak-init --format json 2>/dev/null | jq -r '.[0].State // "not found"' 2>/dev/null || echo "not found")
echo "상태: $STATUS"

echo -e "\n=== 4. keycloak-init 수동 실행 방법 ==="
echo "다음 명령어로 수동 실행:"
echo "  docker compose run --rm keycloak-init"

