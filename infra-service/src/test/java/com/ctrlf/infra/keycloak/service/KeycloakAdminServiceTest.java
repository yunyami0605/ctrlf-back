package com.ctrlf.infra.keycloak.service;

import com.ctrlf.infra.config.metrics.CustomMetrics;
import com.ctrlf.infra.keycloak.KeycloakAdminClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KeycloakAdminService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeycloakAdminService 테스트")
class KeycloakAdminServiceTest {

    @Mock
    private KeycloakAdminClient client;

    @Mock
    private CustomMetrics customMetrics;

    @InjectMocks
    private KeycloakAdminService keycloakAdminService;

    private String testUserId;
    private Map<String, Object> testUser;
    private List<Map<String, Object>> testUserRoles;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";

        testUser = new HashMap<>();
        testUser.put("id", testUserId);
        testUser.put("email", "test@example.com");
        testUser.put("firstName", "Test");
        testUser.put("lastName", "User");
        testUser.put("department", "개발팀");
        testUser.put("position", "시니어 개발자");

        testUserRoles = new ArrayList<>();
        Map<String, Object> role1 = new HashMap<>();
        role1.put("name", "ROLE_USER");
        testUserRoles.add(role1);
        Map<String, Object> role2 = new HashMap<>();
        role2.put("name", "ROLE_ADMIN");
        testUserRoles.add(role2);
    }

    @Test
    @DisplayName("사용자 정보 조회 - 성공")
    void getUser_Success() {
        // given
        when(client.getUser(testUserId)).thenReturn(testUser);
        when(client.getUserRealmRoles(testUserId)).thenReturn(testUserRoles);

        // when
        Map<String, Object> result = keycloakAdminService.getUser(testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(testUserId);
        assertThat(result.get("email")).isEqualTo("test@example.com");
        assertThat(result.get("realmRoles")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) result.get("realmRoles");
        assertThat(roles).contains("ROLE_USER", "ROLE_ADMIN");
        verify(client).getUser(testUserId);
        verify(client).getUserRealmRoles(testUserId);
    }

    @Test
    @DisplayName("사용자 정보 조회 - 사용자를 찾을 수 없음")
    void getUser_NotFound() {
        // given
        when(client.getUser(testUserId)).thenReturn(null);

        // when
        Map<String, Object> result = keycloakAdminService.getUser(testUserId);

        // then
        assertThat(result).isNull();
        verify(client).getUser(testUserId);
    }

    @Test
    @DisplayName("사용자 정보 조회 - 역할 조회 실패 시 빈 리스트 반환")
    void getUser_RoleFetchFailure() {
        // given
        when(client.getUser(testUserId)).thenReturn(testUser);
        when(client.getUserRealmRoles(testUserId)).thenThrow(new RuntimeException("Role fetch failed"));

        // when
        Map<String, Object> result = keycloakAdminService.getUser(testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("realmRoles")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) result.get("realmRoles");
        assertThat(roles).isEmpty();
    }

    @Test
    @DisplayName("개인화용 사용자 정보 조회 - 성공")
    void getUserInfoForPersonalization_Success() {
        // given
        when(client.getUser(testUserId)).thenReturn(testUser);

        // when
        Map<String, Object> result = keycloakAdminService.getUserInfoForPersonalization(testUserId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("email")).isEqualTo("test@example.com");
        assertThat(result.get("firstName")).isEqualTo("Test");
        assertThat(result.get("lastName")).isEqualTo("User");
        assertThat(result.get("department")).isEqualTo("개발팀");
        assertThat(result.get("position")).isEqualTo("시니어 개발자");
        verify(client).getUser(testUserId);
    }

    @Test
    @DisplayName("개인화용 사용자 정보 조회 - 사용자를 찾을 수 없음")
    void getUserInfoForPersonalization_NotFound() {
        // given
        when(client.getUser(testUserId)).thenReturn(null);

        // when
        Map<String, Object> result = keycloakAdminService.getUserInfoForPersonalization(testUserId);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("사용자 생성 - 성공")
    void createUser_Success() {
        // given
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "newuser@example.com");
        payload.put("firstName", "New");
        payload.put("lastName", "User");
        String initialPassword = "password123";
        boolean temporary = true;
        String expectedUserId = "new-user-id";

        when(client.createUser(payload, initialPassword, temporary)).thenReturn(expectedUserId);

        // when
        String result = keycloakAdminService.createUser(payload, initialPassword, temporary);

        // then
        assertThat(result).isEqualTo(expectedUserId);
        verify(client).createUser(payload, initialPassword, temporary);
    }

    @Test
    @DisplayName("사용자 목록 조회 - 성공")
    void listUsers_Success() {
        // given
        String search = "test";
        int page = 0;
        int size = 10;
        com.ctrlf.common.dto.PageResponse<Map<String, Object>> expectedResponse = 
            new com.ctrlf.common.dto.PageResponse<>(List.of(testUser), page, size, 1);

        when(client.listUsers(search, page, size)).thenReturn(expectedResponse);

        // when
        com.ctrlf.common.dto.PageResponse<Map<String, Object>> result = 
            keycloakAdminService.listUsers(search, page, size);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        verify(client).listUsers(search, page, size);
    }

    @Test
    @DisplayName("사용자 정보 업데이트 - 성공")
    void updateUser_Success() {
        // given
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstName", "Updated");

        // when
        keycloakAdminService.updateUser(testUserId, payload);

        // then
        verify(client).updateUser(testUserId, payload);
    }

    @Test
    @DisplayName("비밀번호 재설정 - 성공")
    void resetPassword_Success() {
        // given
        String newPassword = "newPassword123";
        boolean temporary = false;

        // when
        keycloakAdminService.resetPassword(testUserId, newPassword, temporary);

        // then
        verify(client).resetPassword(testUserId, newPassword, temporary);
    }
}
