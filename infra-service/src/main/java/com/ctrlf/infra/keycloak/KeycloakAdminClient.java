package com.ctrlf.infra.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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

    public List<Map<String, Object>> listUsers(String search, int first, int max) {
        String url = adminApi("/users?first=" + first + "&max=" + max + (search != null && !search.isBlank() ? "&search=" + search : ""));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                url, HttpMethod.GET, req, new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            return resp.getBody();
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
}


