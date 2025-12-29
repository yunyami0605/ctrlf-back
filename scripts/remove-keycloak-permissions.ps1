# Keycloak Service Account 권한 제거 스크립트 (테스트용) - PowerShell
# infra-admin 클라이언트의 Service Account에서 realm-management 역할을 제거합니다.

param(
    [string]$KeycloakUrl = "http://localhost:8090",
    [string]$Realm = "ctrlf",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "infra-admin"
)

$ErrorActionPreference = "Stop"

$RealmManagementClient = "realm-management"
$RolesToRemove = @("view-users", "manage-users", "view-realm")

Write-Host "🗑️  Keycloak Service Account 권한 제거 시작..." -ForegroundColor Cyan
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
    $realmMgmtClients = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$RealmManagementClient" `
        -Method Get -Headers $headers
    $realmMgmtClientUuid = $realmMgmtClients[0].id
} catch {
    Write-Host "❌ 'realm-management' 클라이언트를 찾을 수 없습니다." -ForegroundColor Red
    exit 1
}

# realm-management 클라이언트의 역할 목록 조회
Write-Host "📋 'realm-management' 클라이언트 역할 조회 중..." -ForegroundColor Yellow
try {
    $roles = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$realmMgmtClientUuid/roles" `
        -Method Get -Headers $headers
} catch {
    Write-Host "❌ 역할 목록 조회 실패: $_" -ForegroundColor Red
    exit 1
}

# 현재 할당된 역할 조회
Write-Host "📋 현재 할당된 역할 확인 중..." -ForegroundColor Yellow
try {
    $currentRoles = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/users/$serviceAccountUserId/role-mappings/clients/$realmMgmtClientUuid" `
        -Method Get -Headers $headers
} catch {
    $currentRoles = @()
}

$removedCount = 0

# 각 역할 제거
foreach ($roleName in $RolesToRemove) {
    Write-Host "   역할 '$roleName' 확인 중..." -ForegroundColor Cyan
    
    # 역할이 존재하는지 확인
    $role = $roles | Where-Object { $_.name -eq $roleName }
    
    if (-not $role) {
        Write-Host "   ⚠️  역할 '$roleName'이 존재하지 않습니다. 건너뜁니다." -ForegroundColor Yellow
        continue
    }
    
    # 할당되어 있는지 확인
    $assignedRole = $currentRoles | Where-Object { $_.name -eq $roleName }
    
    if (-not $assignedRole) {
        Write-Host "   ℹ️  역할 '$roleName'은 이미 할당되지 않았습니다." -ForegroundColor Gray
        continue
    }
    
    # 역할 제거
    Write-Host "   ➖ 역할 '$roleName' 제거 중..." -ForegroundColor Cyan
    try {
        $roleArray = @($assignedRole)
        $jsonBody = $roleArray | ConvertTo-Json
        
        $response = Invoke-WebRequest -Uri "$KeycloakUrl/admin/realms/$Realm/users/$serviceAccountUserId/role-mappings/clients/$realmMgmtClientUuid" `
            -Method Delete -Headers $headers -Body $jsonBody -ContentType "application/json"
        
        if ($response.StatusCode -eq 204 -or $response.StatusCode -eq 200) {
            Write-Host "   ✅ 역할 '$roleName' 제거 완료" -ForegroundColor Green
            $removedCount++
        } else {
            Write-Host "   ❌ 역할 '$roleName' 제거 실패 (HTTP $($response.StatusCode))" -ForegroundColor Red
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 204 -or $statusCode -eq 200) {
            Write-Host "   ✅ 역할 '$roleName' 제거 완료" -ForegroundColor Green
            $removedCount++
        } else {
            Write-Host "   ❌ 역할 '$roleName' 제거 실패 (HTTP $statusCode): $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
if ($removedCount -gt 0) {
    Write-Host "🎉 제거 완료! $removedCount 개의 역할이 제거되었습니다." -ForegroundColor Green
    Write-Host ""
    Write-Host "📝 제거된 역할:" -ForegroundColor Cyan
    foreach ($roleName in $RolesToRemove) {
        Write-Host "   - $roleName"
    }
    Write-Host ""
    Write-Host "💡 다시 권한을 설정하려면:" -ForegroundColor Cyan
    Write-Host "   .\scripts\setup-keycloak-service-account.ps1"
} else {
    Write-Host "ℹ️  제거할 역할이 없습니다." -ForegroundColor Gray
}

