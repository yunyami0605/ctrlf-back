package com.ctrlf.infra.keycloak;

import com.ctrlf.common.dto.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URI;

@Component
public class KeycloakAdminClient {

    private final KeycloakAdminProperties props;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KeycloakAdminClient(KeycloakAdminProperties props) {
        this.props = props;
    }

    private String tokenEndpoint() {
        return props.getBaseUrl() + "/realms/" + props.getRealm() + "/protocol/openid-connect/token";
    }

    private String adminApi(String path) {
        return props.getBaseUrl() + "/admin/realms/" + props.getRealm() + path;
    }

    public String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        // 역할이 토큰에 포함되도록 scope 명시적 요청
        form.add("scope", "openid email profile");
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        Map<?, ?> resp = restTemplate.postForObject(tokenEndpoint(), entity, Map.class);
        if (resp == null || !resp.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain Keycloak access token");
        }
        return String.valueOf(resp.get("access_token"));
    }

    /**
     * 페이지 기반 사용자 목록 조회
     * @param search 검색어 (옵션)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return PageResponse 객체 (items, page, size, total 포함)
     */
    public PageResponse<Map<String, Object>> listUsers(String search, int page, int size) {
        int first = page * size;
        int max = size;
        String url = adminApi("/users?first=" + first + "&max=" + max + (search != null && !search.isBlank() ? "&search=" + search : ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            
            List<Map<String, Object>> users = resp.getBody();
            if (users == null) {
                users = new ArrayList<>();
            }
            
            // Keycloak은 총 개수를 직접 반환하지 않으므로, 
            // 현재 페이지가 마지막 페이지인지 확인하여 총 개수를 추정
            // 정확한 총 개수를 얻기 위해 추가 조회가 필요할 수 있음
            long total = users.size() < size ? (long) first + users.size() : (long) first + users.size() + 1;
            
            // 더 정확한 총 개수를 얻기 위해 count 쿼리 시도 (Keycloak이 지원하는 경우)
            // 현재는 추정값을 사용하지만, 필요시 별도 count API 호출 가능
            
            return new PageResponse<>(users, page, size, total);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                "Keycloak Admin API 접근 권한이 없습니다. " +
                "Keycloak에서 클라이언트 '" + props.getClientId() + "'의 Service Account가 활성화되어 있고, " +
                "realm-management 클라이언트에 'view-users', 'manage-users' 등의 권한이 할당되어 있는지 확인하세요. " +
                "에러 상세: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak Admin API 호출 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }
    
    /**
     * 사용자 총 개수 조회 (정확한 총 개수를 얻기 위해)
     * @param search 검색어 (옵션)
     * @return 총 사용자 수
     */
    public long countUsers(String search) {
        // Keycloak Admin API는 count 엔드포인트를 제공하지 않으므로,
        // 큰 max 값으로 조회하여 총 개수를 추정하거나
        // 실제로는 모든 페이지를 순회해야 정확한 개수를 얻을 수 있음
        // 성능을 위해 큰 페이지로 한 번 조회하여 추정
        String url = adminApi("/users?first=0&max=1000" + (search != null && !search.isBlank() ? "&search=" + search : ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> users = resp.getBody();
            if (users == null) {
                return 0;
            }
            // 1000개 미만이면 정확한 개수, 1000개면 그 이상일 수 있음
            return users.size() < 1000 ? users.size() : 1000;
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak 사용자 개수 조회 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    public String createUser(Map<String, Object> payload, String initialPassword, boolean temporary) {
        String url = adminApi("/users");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<Void> resp = restTemplate.postForEntity(url, req, Void.class);
            URI loc = resp.getHeaders().getLocation();
            String userId = null;
            if (loc != null) {
                String path = loc.getPath();
                int idx = path.lastIndexOf('/');
                if (idx >= 0 && idx < path.length()-1) {
                    userId = path.substring(idx+1);
                }
            }
            if (userId == null) {
                throw new IllegalStateException("Failed to parse created user id from Location header");
            }
            if (initialPassword != null && !initialPassword.isBlank()) {
                resetPassword(userId, initialPassword, temporary);
            }
            return userId;
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                "Keycloak Admin API 접근 권한이 없습니다. " +
                "Keycloak에서 클라이언트 '" + props.getClientId() + "'의 Service Account가 활성화되어 있고, " +
                "realm-management 클라이언트에 'manage-users' 권한이 할당되어 있는지 확인하세요. " +
                "에러 상세: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak Admin API 호출 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    public void updateUser(String userId, Map<String, Object> payload) {
        String url = adminApi("/users/" + userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        try {
            restTemplate.exchange(url, HttpMethod.PUT, req, Void.class);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                "Keycloak Admin API 접근 권한이 없습니다. " +
                "Keycloak에서 클라이언트 '" + props.getClientId() + "'의 Service Account가 활성화되어 있고, " +
                "realm-management 클라이언트에 'manage-users' 권한이 할당되어 있는지 확인하세요. " +
                "에러 상세: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak Admin API 호출 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    public void resetPassword(String userId, String newPassword, boolean temporary) {
        String url = adminApi("/users/" + userId + "/reset-password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = Map.of(
            "type", "password",
            "value", newPassword,
            "temporary", temporary
        );
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        try {
            restTemplate.exchange(url, HttpMethod.PUT, req, Void.class);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                "Keycloak Admin API 접근 권한이 없습니다. " +
                "Keycloak에서 클라이언트 '" + props.getClientId() + "'의 Service Account가 활성화되어 있고, " +
                "realm-management 클라이언트에 'manage-users' 권한이 할당되어 있는지 확인하세요. " +
                "에러 상세: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak Admin API 호출 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getUser(String userId) {
        String url = adminApi("/users/" + userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<Map<String, Object>>() {});
            return resp.getBody();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new IllegalStateException(
                "Keycloak Admin API 접근 권한이 없습니다. " +
                "Keycloak에서 클라이언트 '" + props.getClientId() + "'의 Service Account가 활성화되어 있고, " +
                "realm-management 클라이언트에 'view-users' 권한이 할당되어 있는지 확인하세요. " +
                "에러 상세: " + e.getMessage(), e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak Admin API 호출 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    private String userInfoEndpoint() {
        return props.getBaseUrl() + "/realms/" + props.getRealm() + "/protocol/openid-connect/userinfo";
    }

    private String introspectEndpoint() {
        return props.getBaseUrl() + "/realms/" + props.getRealm() + "/protocol/openid-connect/token/introspect";
    }

    /**
     * 주어진 액세스 토큰에 해당하는 사용자 정보를 조회합니다.
     * Authorization: Bearer {accessToken} 으로 userinfo 엔드포인트를 호출합니다.
     */
    public Map<String, Object> getUserInfoWithToken(String accessToken) {
        String token = accessToken;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("accessToken is required");
        }
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            userInfoEndpoint(), HttpMethod.GET, req, new ParameterizedTypeReference<Map<String, Object>>() {});
        return resp.getBody();
    }

    /**
     * 토큰 인트로스펙션을 수행하여 토큰의 활성 여부 및 클레임을 반환합니다.
     * clientId/secret로 인증하여 { active, client_id, username, scope, sub, exp ... } 등을 반환합니다.
     */
    public Map<String, Object> introspectToken(String accessToken) {
        String token = accessToken;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("accessToken is required");
        }
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        // client 인증 (confidential)
        form.add("client_id", props.getClientId());
        if (props.getClientSecret() != null && !props.getClientSecret().isBlank()) {
            form.add("client_secret", props.getClientSecret());
        }
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = restTemplate.postForObject(introspectEndpoint(), entity, Map.class);
        return body;
    }

    /**
     * 현재 설정된 클라이언트의 Service Account 토큰을 인트로스펙션하여 권한 정보를 확인합니다.
     * 디버깅 목적으로 사용됩니다.
     */
    public Map<String, Object> introspectServiceAccountToken() {
        String token = getAccessToken();
        return introspectToken(token);
    }

    /**
     * JWT 토큰의 payload를 디코딩하여 반환합니다.
     * Base64로 인코딩된 JWT의 두 번째 부분(payload)을 디코딩합니다.
     */
    public Map<String, Object> decodeTokenPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }
            String payload = parts[1];
            // Base64 URL-safe 디코딩
            byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(payload);
            String jsonPayload = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(jsonPayload, Map.class);
            return payloadMap;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * 현재 설정된 클라이언트의 Service Account 토큰을 디코딩하여 전체 클레임을 확인합니다.
     * 디버깅 목적으로 사용됩니다.
     */
    public Map<String, Object> decodeServiceAccountToken() {
        String token = getAccessToken();
        return decodeTokenPayload(token);
    }

    // ===== Role Management =====

    /**
     * 사용 가능한 모든 realm 역할 목록을 조회합니다.
     * Keycloak 기본 역할(default-roles-*, uma_authorization, offline_access 등)은 제외하고
     * 커스텀 역할만 반환합니다.
     */
    public List<Map<String, Object>> getRealmRoles() {
        String url = adminApi("/roles");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> allRoles = resp.getBody();
            if (allRoles == null) {
                return new ArrayList<>();
            }
            
            // 커스텀 역할만 필터링 (Keycloak 기본 역할 제외)
            // 커스텀 역할: SYSTEM_ADMIN, COMPLAINT_MANAGER, VIDEO_CREATOR, CONTENTS_REVIEWER, EMPLOYEE
            List<String> customRoleNames = List.of(
                "SYSTEM_ADMIN",
                "COMPLAINT_MANAGER",
                "VIDEO_CREATOR",
                "CONTENTS_REVIEWER",
                "EMPLOYEE"
            );
            
            return allRoles.stream()
                .filter(role -> {
                    String roleName = (String) role.get("name");
                    return roleName != null && customRoleNames.contains(roleName);
                })
                .toList();
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak 역할 조회 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 특정 사용자에게 할당된 realm 역할 목록을 조회합니다.
     * 커스텀 역할만 반환합니다 (Keycloak 기본 역할 제외).
     */
    public List<Map<String, Object>> getUserRealmRoles(String userId) {
        String url = adminApi("/users/" + userId + "/role-mappings/realm");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> allRoles = resp.getBody();
            if (allRoles == null) {
                return new ArrayList<>();
            }
            
            // 커스텀 역할만 필터링
            List<String> customRoleNames = List.of(
                "SYSTEM_ADMIN",
                "COMPLAINT_MANAGER",
                "VIDEO_CREATOR",
                "CONTENTS_REVIEWER",
                "EMPLOYEE"
            );
            
            return allRoles.stream()
                .filter(role -> {
                    String roleName = (String) role.get("name");
                    return roleName != null && customRoleNames.contains(roleName);
                })
                .toList();
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다: " + userId, e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak 사용자 역할 조회 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 사용자에게 realm 역할을 할당합니다.
     */
    public void assignRealmRolesToUser(String userId, List<Map<String, Object>> roles) {
        String url = adminApi("/users/" + userId + "/role-mappings/realm");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String, Object>>> req = new HttpEntity<>(roles, headers);
        try {
            restTemplate.exchange(url, HttpMethod.POST, req, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("사용자 또는 역할을 찾을 수 없습니다: " + userId, e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak 역할 할당 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 사용자로부터 realm 역할을 제거합니다.
     */
    public void removeRealmRolesFromUser(String userId, List<Map<String, Object>> roles) {
        String url = adminApi("/users/" + userId + "/role-mappings/realm");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String, Object>>> req = new HttpEntity<>(roles, headers);
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, req, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalStateException("사용자 또는 역할을 찾을 수 없습니다: " + userId, e);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException(
                "Keycloak 역할 제거 실패: " + e.getStatusCode() + " - " + e.getMessage(), e);
        }
    }
}
