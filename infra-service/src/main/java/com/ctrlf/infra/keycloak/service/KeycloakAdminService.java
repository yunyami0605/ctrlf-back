package com.ctrlf.infra.keycloak.service;

import com.ctrlf.infra.keycloak.KeycloakAdminClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakAdminService {

    private final KeycloakAdminClient client;

    public KeycloakAdminService(KeycloakAdminClient client) {
        this.client = client;
    }

    public Map<String, Object> getUser(String userId) {
        return client.getUser(userId);
    }

    public String createUser(Map<String, Object> payload, String initialPassword, boolean temporary) {
        return client.createUser(payload, initialPassword, temporary);
    }

    public com.ctrlf.common.dto.PageResponse<Map<String, Object>> listUsers(String search, int page, int size) {
        return client.listUsers(search, page, size);
    }

    public void updateUser(String userId, Map<String, Object> payload) {
        client.updateUser(userId, payload);
    }

    public void resetPassword(String userId, String newPassword, boolean temporary) {
        client.resetPassword(userId, newPassword, temporary);
    }

    /**
     * Authorization 헤더의 토큰으로 사용자 정보를 조회합니다.
     * userinfo 엔드포인트를 호출하고, 실패 시 인트로스펙션을 시도합니다.
     */
    public Map<String, Object> getCurrentUserInfo(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalArgumentException("Authorization header is required");
        }
        String token = authorization;
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        try {
            // 먼저 userinfo 엔드포인트 시도
            return client.getUserInfoWithToken(token);
        } catch (Exception e) {
            // userinfo 실패 시 인트로스펙션 시도
            return client.introspectToken(token);
        }
    }

    /**
     * 여러 사용자를 일괄 생성합니다.
     */
    public List<Map<String, Object>> createUsersBatch(List<Map<String, Object>> requests) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> req : requests) {
            Map<String, Object> result = new java.util.HashMap<>();
            try {
                String username = (String) req.get("username");
                String email = (String) req.get("email");
                String firstName = (String) req.get("firstName");
                String lastName = (String) req.get("lastName");
                Boolean enabled = (Boolean) req.getOrDefault("enabled", true);
                @SuppressWarnings("unchecked")
                Map<String, Object> attributes = (Map<String, Object>) req.get("attributes");
                String initialPassword = (String) req.get("initialPassword");
                Boolean temporaryPassword = (Boolean) req.getOrDefault("temporaryPassword", false);

                Map<String, Object> payload = new java.util.HashMap<>();
                if (username != null) payload.put("username", username);
                if (email != null) payload.put("email", email);
                if (firstName != null) payload.put("firstName", firstName);
                if (lastName != null) payload.put("lastName", lastName);
                payload.put("enabled", enabled);
                if (attributes != null) payload.put("attributes", attributes);

                String userId = client.createUser(payload, initialPassword, temporaryPassword);
                result.put("success", true);
                result.put("userId", userId);
                result.put("username", username);
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("username", req.get("username"));
            }
            results.add(result);
        }
        return results;
    }

    /**
     * KeycloakAdminClient 인스턴스를 반환합니다.
     * 디버깅 목적으로 사용됩니다.
     */
    public KeycloakAdminClient getClient() {
        return client;
    }

    // ===== Role Management =====

    /**
     * 사용 가능한 모든 realm 역할 목록을 조회합니다.
     */
    public List<Map<String, Object>> getRealmRoles() {
        return client.getRealmRoles();
    }

    /**
     * 특정 사용자에게 할당된 realm 역할 목록을 조회합니다.
     */
    public List<Map<String, Object>> getUserRealmRoles(String userId) {
        return client.getUserRealmRoles(userId);
    }

    /**
     * 사용자에게 realm 역할을 할당합니다.
     */
    public void assignRealmRolesToUser(String userId, List<Map<String, Object>> roles) {
        client.assignRealmRolesToUser(userId, roles);
    }

    /**
     * 사용자로부터 realm 역할을 제거합니다.
     */
    public void removeRealmRolesFromUser(String userId, List<Map<String, Object>> roles) {
        client.removeRealmRolesFromUser(userId, roles);
    }

    /**
     * 사용자의 커스텀 역할을 업데이트합니다.
     * 기존 커스텀 역할을 모두 제거하고 새로운 커스텀 역할 목록을 할당합니다.
     * Keycloak 기본 역할(default-roles-*, uma_authorization, offline_access 등)은 유지됩니다.
     * 
     * @param userId 사용자 ID
     * @param roleNames 할당할 커스텀 역할 이름 목록 (예: ["SYSTEM_ADMIN", "EMPLOYEE"])
     */
    public void updateUserRoles(String userId, List<String> roleNames) {
        // 1. 모든 커스텀 realm 역할 조회
        List<Map<String, Object>> allCustomRoles = client.getRealmRoles();
        
        // 2. 현재 사용자의 커스텀 역할 조회 (getUserRealmRoles는 이미 필터링된 결과 반환)
        List<Map<String, Object>> currentCustomRoles = client.getUserRealmRoles(userId);
        
        // 3. 제거할 커스텀 역할 찾기 (현재 커스텀 역할 중 새 역할 목록에 없는 것)
        List<Map<String, Object>> rolesToRemove = currentCustomRoles.stream()
            .filter(role -> {
                String roleName = (String) role.get("name");
                return roleName != null && !roleNames.contains(roleName);
            })
            .toList();
        
        // 4. 추가할 커스텀 역할 찾기 (새 역할 중 현재 역할에 없는 것)
        List<Map<String, Object>> rolesToAdd = allCustomRoles.stream()
            .filter(role -> {
                String roleName = (String) role.get("name");
                return roleName != null && roleNames.contains(roleName) 
                    && !currentCustomRoles.stream().anyMatch(cr -> cr.get("name").equals(roleName));
            })
            .toList();
        
        // 5. 역할 제거
        if (!rolesToRemove.isEmpty()) {
            client.removeRealmRolesFromUser(userId, rolesToRemove);
        }
        
        // 6. 역할 추가
        if (!rolesToAdd.isEmpty()) {
            client.assignRealmRolesToUser(userId, rolesToAdd);
        }
    }

    /**
     * 부서와 역할로 필터링된 사용자 목록을 조회합니다.
     * Keycloak의 search 파라미터는 제한적이므로, 클라이언트 측에서 필터링합니다.
     */
    public com.ctrlf.common.dto.PageResponse<Map<String, Object>> listUsersWithFilters(
        String search, 
        String department, 
        String roleName,
        int page, 
        int size
    ) {
        // 먼저 모든 사용자 조회 (검색어가 있으면 적용)
        // Keycloak은 필터링을 지원하지 않으므로, 큰 페이지로 조회 후 클라이언트 측에서 필터링
        com.ctrlf.common.dto.PageResponse<Map<String, Object>> allUsersPage = client.listUsers(search, 0, 1000);
        List<Map<String, Object>> allUsers = allUsersPage.getItems();
        
        // 부서 필터링
        if (department != null && !department.isBlank() && !department.equals("전체 부서")) {
            allUsers = allUsers.stream()
                .filter(user -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attrs = (Map<String, Object>) user.get("attributes");
                    if (attrs == null) return false;
                    @SuppressWarnings("unchecked")
                    List<String> deptList = (List<String>) attrs.get("department");
                    return deptList != null && deptList.contains(department);
                })
                .toList();
        }
        
        // 역할 필터링
        if (roleName != null && !roleName.isBlank() && !roleName.equals("전체 역할")) {
            allUsers = allUsers.stream()
                .filter(user -> {
                    try {
                        List<Map<String, Object>> userRoles = client.getUserRealmRoles((String) user.get("id"));
                        return userRoles.stream()
                            .anyMatch(role -> roleName.equals(role.get("name")));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();
        }
        
        // 페이징 적용
        int start = page * size;
        int end = Math.min(start + size, allUsers.size());
        List<Map<String, Object>> pagedUsers;
        if (start >= allUsers.size()) {
            pagedUsers = new ArrayList<>();
        } else {
            pagedUsers = allUsers.subList(start, end);
        }
        
        // 각 사용자에 역할 정보 추가 (역할 이름만 배열로)
        for (Map<String, Object> user : pagedUsers) {
            try {
                String userId = (String) user.get("id");
                if (userId != null) {
                    List<Map<String, Object>> userRoles = client.getUserRealmRoles(userId);
                    // 역할 이름만 추출하여 배열로 변환
                    List<String> roleNames = userRoles.stream()
                        .map(role -> (String) role.get("name"))
                        .filter(name -> name != null)
                        .toList();
                    user.put("realmRoles", roleNames);
                }
            } catch (Exception e) {
                // 역할 조회 실패 시 빈 리스트로 설정
                user.put("realmRoles", new ArrayList<>());
            }
        }
        
        // 총 개수는 필터링된 전체 개수
        long total = allUsers.size();
        
        return new com.ctrlf.common.dto.PageResponse<>(pagedUsers, page, size, total);
    }
}
