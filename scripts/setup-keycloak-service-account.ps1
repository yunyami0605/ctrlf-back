# Keycloak Service Account 권한 자동 설정 스크립트 (PowerShell)
# infra-admin 클라이언트의 Service Account에 realm-management 역할을 할당합니다.

param(
    [string]$KeycloakUrl = "http://localhost:8090",
    [string]$Realm = "ctrlf",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "infra-admin"
)

$ErrorActionPreference = "Stop"

$RealmManagementClient = "realm-management"
$RequiredRoles = @("view-users", "manage-users", "view-realm")

Write-Host "🔐 Keycloak Service Account 권한 설정 시작..." -ForegroundColor Cyan
Write-Host "   Keycloak URL: $KeycloakUrl"
Write-Host "   Realm: $Realm"
Write-Host "   Client: $ClientId"
Write-Host ""

# Keycloak이 준비될 때까지 대기
Write-Host "⏳ Keycloak이 준비될 때까지 대기 중..." -ForegroundColor Yellow
$maxRetries = 30
$retryCount = 0
$keycloakReady = $false

while ($retryCount -lt $maxRetries) {
    try {
        $response = Invoke-WebRequest -Uri "$KeycloakUrl/health" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Host "✅ Keycloak이 준비되었습니다." -ForegroundColor Green
            $keycloakReady = $true
            break
        }
    } catch {
        # 계속 대기
    }
    $retryCount++
    Start-Sleep -Seconds 1
}

if (-not $keycloakReady) {
    Write-Host "❌ Keycloak이 30초 내에 준비되지 않았습니다." -ForegroundColor Red
    exit 1
}

# Realm이 import될 때까지 대기
Write-Host "⏳ Realm import 완료 대기 중..." -ForegroundColor Yellow
$maxRetries = 60
$retryCount = 0
$realmReady = $false

while ($retryCount -lt $maxRetries) {
    try {
        # 관리자 토큰 획득
        $tokenBody = @{
            username = $AdminUser
            password = $AdminPassword
            grant_type = "password"
            client_id = "admin-cli"
        }
        
        $tokenResponse = Invoke-RestMethod -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
            -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded" -ErrorAction SilentlyContinue
        
        if ($tokenResponse.access_token) {
            $headers = @{
                Authorization = "Bearer $($tokenResponse.access_token)"
            }
            
            $realmResponse = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm" `
                -Method Get -Headers $headers -ErrorAction SilentlyContinue
            
            if ($realmResponse.realm -eq $Realm) {
                Write-Host "✅ Realm '$Realm'이 준비되었습니다." -ForegroundColor Green
                $realmReady = $true
                break
            }
        }
    } catch {
        # 계속 대기
    }
    $retryCount++
    Start-Sleep -Seconds 1
}

if (-not $realmReady) {
    Write-Host "❌ Realm '$Realm'이 60초 내에 준비되지 않았습니다." -ForegroundColor Red
    exit 1
}

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
    $clientUuid = $null
}

if (-not $clientUuid) {
    Write-Host "❌ 클라이언트 '$ClientId'를 찾을 수 없습니다." -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 해결 방법:" -ForegroundColor Cyan
    Write-Host "   1. Keycloak Admin Console에서 수동으로 클라이언트를 생성하거나"
    Write-Host "   2. Keycloak 볼륨을 삭제하고 재시작하여 realm import를 다시 수행하세요:"
    Write-Host "      docker compose down"
    Write-Host "      docker volume rm ctrlf-back_kc-db-data"
    Write-Host "      docker compose up -d keycloak"
    Write-Host ""
    Write-Host "   클라이언트 설정:" -ForegroundColor Cyan
    Write-Host "   - Client ID: $ClientId"
    Write-Host "   - Client authentication: ON"
    Write-Host "   - Service accounts roles: ON"
    Write-Host "   - Secret: changeme"
    exit 1
}

# Service Account 사용자 ID 조회
Write-Host "👤 Service Account 사용자 조회 중..." -ForegroundColor Yellow
try {
    $serviceAccountUser = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/service-account-user" `
        -Method Get -Headers $headers
    $serviceAccountUserId = $serviceAccountUser.id
} catch {
    Write-Host "❌ Service Account 사용자를 찾을 수 없습니다. 클라이언트의 'Service accounts enabled'가 활성화되어 있는지 확인하세요." -ForegroundColor Red
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

$assignedCount = 0

# 각 역할 할당
foreach ($roleName in $RequiredRoles) {
    Write-Host "   역할 '$roleName' 확인 중..." -ForegroundColor Cyan
    
    # 역할이 존재하는지 확인
    $role = $roles | Where-Object { $_.name -eq $roleName }
    
    if (-not $role) {
        Write-Host "   ⚠️  역할 '$roleName'이 존재하지 않습니다. 건너뜁니다." -ForegroundColor Yellow
        continue
    }
    
    # 이미 할당되어 있는지 확인
    $alreadyAssigned = $currentRoles | Where-Object { $_.name -eq $roleName }
    
    if ($alreadyAssigned) {
        Write-Host "   ✅ 역할 '$roleName'은 이미 할당되어 있습니다." -ForegroundColor Green
        continue
    }
    
    # 역할 할당
    Write-Host "   ➕ 역할 '$roleName' 할당 중..." -ForegroundColor Cyan
    try {
        $roleArray = @($role)
        $jsonBody = $roleArray | ConvertTo-Json
        
        $response = Invoke-WebRequest -Uri "$KeycloakUrl/admin/realms/$Realm/users/$serviceAccountUserId/role-mappings/clients/$realmMgmtClientUuid" `
            -Method Post -Headers $headers -Body $jsonBody -ContentType "application/json"
        
        if ($response.StatusCode -eq 204 -or $response.StatusCode -eq 200) {
            Write-Host "   ✅ 역할 '$roleName' 할당 완료" -ForegroundColor Green
            $assignedCount++
        } else {
            Write-Host "   ❌ 역할 '$roleName' 할당 실패 (HTTP $($response.StatusCode))" -ForegroundColor Red
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 204 -or $statusCode -eq 200) {
            Write-Host "   ✅ 역할 '$roleName' 할당 완료" -ForegroundColor Green
            $assignedCount++
        } else {
            Write-Host "   ❌ 역할 '$roleName' 할당 실패 (HTTP $statusCode): $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "🎉 설정 완료! $assignedCount 개의 역할이 할당되었습니다." -ForegroundColor Green
Write-Host ""
Write-Host "📝 할당된 역할:" -ForegroundColor Cyan
foreach ($roleName in $RequiredRoles) {
    Write-Host "   - $roleName"
}
Write-Host ""
Write-Host "💡 권한 확인:" -ForegroundColor Cyan
Write-Host "   curl http://localhost:9003/admin/users/token/decode | jq"
Write-Host "   또는 PowerShell:"
Write-Host "   Invoke-RestMethod http://localhost:9003/admin/users/token/decode | ConvertTo-Json"

