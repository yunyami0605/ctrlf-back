package com.ctrlf.infra.keycloak.service;

import com.ctrlf.infra.keycloak.KeycloakAdminClient;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final KeycloakAdminClient client;

    public KeycloakAdminService(
            KeycloakAdminClient client) {
        this.client = client;
    }

    /**
     * 사용자 정보를 조회하고, Keycloak DB의 user_attribute 테이블에서 attributes를 가져와 추가합니다.
     */
    public Map<String, Object> getUser(String userId) {
        Map<String, Object> user = client.getUser(userId);

        if (user == null) {
            return null;
        }

        return user;
    }

    /**
     * 개인화를 위한 사용자 정보를 조회합니다.
     * Keycloak에서 사용자 정보를 가져와 모든 속성을 반환합니다.
     * 
     * @param userId Keycloak 사용자 ID
     * @return 사용자 정보 Map (모든 Keycloak 속성 포함: department, position, email, unusedVacationDays, employeeNo, fullName, gender, age, tenureYears, hireYear, overtimeHours, performanceScore, salary, companyEmail, phoneNumber 등)
     */
    public Map<String, Object> getUserInfoForPersonalization(String userId) {
        try {
            Map<String, Object> user = client.getUser(userId);
            
            if (user == null) {
                log.warn("Keycloak에서 사용자를 찾을 수 없음: userId={}", userId);
                return null;
            }
            
            // Keycloak에서 가져온 모든 속성을 그대로 반환
            // 이미 KeycloakAdminClient.getUser()에서 attributes의 모든 값이 최상위 레벨에 추가됨
            Map<String, Object> result = new java.util.HashMap<>();
            
            // 기본 정보
            result.put("email", user.get("email") != null ? user.get("email") : "");
            result.put("firstName", user.get("firstName") != null ? user.get("firstName") : "");
            result.put("lastName", user.get("lastName") != null ? user.get("lastName") : "");
            
            // Keycloak attributes에서 추출된 모든 속성 추가
            result.put("department", user.get("department") != null ? user.get("department") : "");
            result.put("position", user.get("position") != null ? user.get("position") : "");
            result.put("unusedVacationDays", user.get("unusedVacationDays") != null ? user.get("unusedVacationDays") : "");
            result.put("employeeNo", user.get("employeeNo") != null ? user.get("employeeNo") : "");
            result.put("fullName", user.get("fullName") != null ? user.get("fullName") : "");
            result.put("gender", user.get("gender") != null ? user.get("gender") : "");
            result.put("age", user.get("age") != null ? user.get("age") : "");
            result.put("tenureYears", user.get("tenureYears") != null ? user.get("tenureYears") : "");
            result.put("hireYear", user.get("hireYear") != null ? user.get("hireYear") : "");
            result.put("overtimeHours", user.get("overtimeHours") != null ? user.get("overtimeHours") : "");
            result.put("performanceScore", user.get("performanceScore") != null ? user.get("performanceScore") : "");
            result.put("salary", user.get("salary") != null ? user.get("salary") : "");
            result.put("companyEmail", user.get("companyEmail") != null ? user.get("companyEmail") : "");
            result.put("phoneNumber", user.get("phoneNumber") != null ? user.get("phoneNumber") : "");
            
            log.debug("Keycloak 사용자 정보 조회 성공: userId={}, department={}, position={}, email={}", 
                userId, result.get("department"), result.get("position"), result.get("email"));
            
            return result;
        } catch (IllegalStateException e) {
            log.error("Keycloak Admin API 호출 실패: userId={}, error={}", userId, e.getMessage(), e);
            throw e; // 예외를 다시 던져서 상위에서 처리하도록
        } catch (Exception e) {
            log.error("Keycloak 사용자 정보 조회 중 예상치 못한 오류: userId={}, error={}", userId, e.getMessage(), e);
            throw new IllegalStateException("Keycloak에서 사용자 정보를 조회하는 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
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

    /**
     * 부서와 역할로 필터링된 사용자 수를 조회합니다.
     * @param search 검색어 (옵션)
     * @param department 부서 필터 (옵션)
     * @param roleName 역할 필터 (옵션)
     * @return 필터링된 사용자 수
     */
    public long countUsersWithFilters(String search, String department, String roleName) {
        // 먼저 모든 사용자 조회 (검색어가 있으면 적용)
        com.ctrlf.common.dto.PageResponse<Map<String, Object>> allUsersPage = client.listUsers(search, 0, 1000);
        List<Map<String, Object>> allUsers = allUsersPage.getItems();
        
        // 부서 필터링
        if (department != null && !department.isBlank() && !department.equals("전체 부서")) {
            // URL 디코딩
            String decodedDepartment;
            try {
                decodedDepartment = URLDecoder.decode(department, StandardCharsets.UTF_8);
            } catch (Exception e) {
                decodedDepartment = department; // 디코딩 실패 시 원본 사용
            }
            
            final String finalDepartment = decodedDepartment;
            allUsers = allUsers.stream()
                .filter(user -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attrs = (Map<String, Object>) user.get("attributes");
                    if (attrs == null) return false;
                    @SuppressWarnings("unchecked")
                    List<String> deptList = (List<String>) attrs.get("department");
                    return deptList != null && deptList.contains(finalDepartment);
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
        
        return allUsers.size();
    }
}
