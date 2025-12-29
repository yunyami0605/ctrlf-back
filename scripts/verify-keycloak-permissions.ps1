# Keycloak Service Account 권한 확인 스크립트 (PowerShell)

param(
    [string]$KeycloakUrl = "http://localhost:8090",
    [string]$Realm = "ctrlf",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "infra-admin"
)

$ErrorActionPreference = "Stop"

Write-Host "🔍 Keycloak Service Account 권한 확인 중..." -ForegroundColor Cyan
Write-Host "   Keycloak URL: $KeycloakUrl"
Write-Host "   Realm: $Realm"
Write-Host "   Client: $ClientId"
Write-Host ""

# 관리자 토큰 획득
Write-Host "🔑 관리자 토큰 획득 중..." -ForegroundColor Yellow
$tokenBody = @{
    username = $AdminUser
    password = $AdminPassword
    grant_type = "password"
    client_id = "admin-cli"
}

try {
    $tokenResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
    $adminToken = $tokenResponse.access_token
} catch {
    Write-Host "❌ 관리자 토큰 획득 실패: $_" -ForegroundColor Red
    exit 1
}

if (-not $adminToken) {
    Write-Host "❌ 관리자 토큰 획득 실패" -ForegroundColor Red
    exit 1
}

$headers = @{
    Authorization = "Bearer $adminToken"
}

# infra-admin 클라이언트 ID 조회
Write-Host "🔍 '$ClientId' 클라이언트 조회 중..." -ForegroundColor Yellow
try {
    $clients = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" `
        -Method Get -Headers $headers
    $clientUuid = $clients[0].id
} catch {
    Write-Host "❌ 클라이언트 '$ClientId'를 찾을 수 없습니다." -ForegroundColor Red
    exit 1
}

# Service Account 사용자 ID 조회
Write-Host "👤 Service Account 사용자 조회 중..." -ForegroundColor Yellow
try {
    $serviceAccountUser = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/service-account-user" `
        -Method Get -Headers $headers
    $serviceAccountUserId = $serviceAccountUser.id
} catch {
    Write-Host "❌ Service Account 사용자를 찾을 수 없습니다." -ForegroundColor Red
    exit 1
}

# realm-management 클라이언트 ID 조회
Write-Host "🔍 'realm-management' 클라이언트 조회 중..." -ForegroundColor Yellow
try {
    $realmMgmtClients = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=realm-management" `
        -Method Get -Headers $headers
    $realmMgmtClientUuid = $realmMgmtClients[0].id
} catch {
    Write-Host "❌ 'realm-management' 클라이언트를 찾을 수 없습니다." -ForegroundColor Red
    exit 1
}

# 할당된 역할 조회
Write-Host ""
Write-Host "📋 할당된 역할 확인 중..." -ForegroundColor Yellow
try {
    $assignedRoles = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/users/$serviceAccountUserId/role-mappings/clients/$realmMgmtClientUuid" `
        -Method Get -Headers $headers
} catch {
    Write-Host "❌ 역할 조회 실패: $_" -ForegroundColor Red
    exit 1
}

if ($assignedRoles.Count -eq 0) {
    Write-Host "❌ 할당된 역할이 없습니다!" -ForegroundColor Red
    exit 1
}

$assignedRoleNames = $assignedRoles | ForEach-Object { $_.name }

Write-Host ""
Write-Host "✅ 할당된 역할 목록:" -ForegroundColor Green
foreach ($role in $assignedRoleNames) {
    Write-Host "   - $role"
}

# 필수 역할 확인
$requiredRoles = @("view-users", "manage-users", "view-realm")
$missingRoles = @()

Write-Host ""
Write-Host "🔍 필수 역할 확인 중..." -ForegroundColor Yellow
foreach ($role in $requiredRoles) {
    if ($assignedRoleNames -contains $role) {
        Write-Host "   ✅ $role : 할당됨" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $role : 할당되지 않음" -ForegroundColor Red
        $missingRoles += $role
    }
}

Write-Host ""
if ($missingRoles.Count -eq 0) {
    Write-Host "🎉 모든 필수 역할이 정상적으로 할당되었습니다!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "⚠️  다음 역할이 할당되지 않았습니다: $($missingRoles -join ', ')" -ForegroundColor Yellow
    exit 1
}

