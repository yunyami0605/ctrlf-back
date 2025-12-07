package com.ctrlf.infra.keycloak;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class KeycloakAdminService {

    private final KeycloakAdminClient client;

    public KeycloakAdminService(KeycloakAdminClient client) {
        this.client = client;
    }

    public List<Map<String, Object>> listUsers(String search, int page, int size) {
        int first = Math.max(0, page) * Math.max(1, size);
        int max = Math.max(1, Math.min(size, 200));
        return client.listUsers(search, first, max);
    }

    public void updateUser(String userId, Map<String, Object> payload) {
        client.updateUser(userId, payload);
    }

    public void resetPassword(String userId, String newPassword, boolean temporary) {
        client.resetPassword(userId, newPassword, temporary);
    }

    public String createUser(Map<String, Object> payload, String initialPassword, boolean temporary) {
        return client.createUser(payload, initialPassword, temporary);
    }

    public List<Map<String, Object>> createUsersBatch(List<Map<String, Object>> requests) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> req : requests) {
            Map<String, Object> result = new HashMap<>();
            String username = req.get("username") != null ? String.valueOf(req.get("username")) : null;
            result.put("username", username);
            try {
                String initialPassword = req.get("initialPassword") != null ? String.valueOf(req.get("initialPassword")) : null;
                boolean temporary = req.get("temporaryPassword") instanceof Boolean ? (Boolean) req.get("temporaryPassword") : false;

                Map<String, Object> payload = new HashMap<>();
                if (req.get("username") != null) payload.put("username", req.get("username"));
                if (req.get("email") != null) payload.put("email", req.get("email"));
                if (req.get("firstName") != null) payload.put("firstName", req.get("firstName"));
                if (req.get("lastName") != null) payload.put("lastName", req.get("lastName"));
                payload.put("enabled", req.get("enabled") instanceof Boolean ? req.get("enabled") : Boolean.TRUE);
                if (req.get("attributes") instanceof Map<?, ?> attrs) {
                    payload.put("attributes", attrs);
                }

                String userId = client.createUser(payload, initialPassword, temporary);
                result.put("success", true);
                result.put("userId", userId);
            } catch (HttpClientErrorException e) {
                result.put("success", false);
                result.put("status", e.getStatusCode().value());
                result.put("error", e.getStatusText());
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }
}


