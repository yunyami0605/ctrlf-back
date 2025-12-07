package com.ctrlf.infra.keycloak;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.net.URI;

@Component
public class KeycloakAdminClient {

    private final KeycloakAdminProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

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
        ResponseEntity<List> resp = restTemplate.exchange(url, HttpMethod.GET, req, List.class);
        return resp.getBody();
    }

    public String createUser(Map<String, Object> payload, String initialPassword, boolean temporary) {
        String url = adminApi("/users");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
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
    }

    public void updateUser(String userId, Map<String, Object> payload) {
        String url = adminApi("/users/" + userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(payload, headers);
        restTemplate.exchange(url, HttpMethod.PUT, req, Void.class);
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
        restTemplate.exchange(url, HttpMethod.PUT, req, Void.class);
    }
}


