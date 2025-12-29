package com.ctrlf.infra.keycloak.controller;

import com.ctrlf.infra.keycloak.dto.UserRequestDto;
import com.ctrlf.infra.keycloak.dto.PasswordTokenRequest;
import com.ctrlf.infra.keycloak.service.KeycloakAdminService;
import com.ctrlf.infra.keycloak.KeycloakAdminProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keycloak Admin API 연동을 통해 관리 사용자를 조회/생성/수정/비밀번호 초기화하는 컨트롤러.
 *
 * Base URL: /admin/users
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "Admin Users", description = "Keycloak 관리 사용자 API")
public class AdminUserController {

    private final KeycloakAdminService service;
    private final KeycloakAdminProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    public AdminUserController(KeycloakAdminService service, KeycloakAdminProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping("/{userId}")
    @Operation(
        summary = "관리 사용자 단건 조회",
        description = "Keycloak Admin API의 /users/{id} 결과를 반환합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Map<String, Object>> getUser(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId
    ) {
        Map<String, Object> user = service.getUser(userId);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/register")
    @Operation(
        summary = "관리 사용자 등록",
        description = "사용자 정보를 등록(생성)합니다. 기존 POST /admin/users 와 동일 동작을 별도 엔드포인트로 제공합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공 - 생성된 사용자 ID 반환",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<Map<String, String>> register(@RequestBody UserRequestDto.CreateUserReq req) {
        Map<String, Object> payload = new HashMap<>();
        if (req.getUsername() != null) payload.put("username", req.getUsername());
        if (req.getEmail() != null) payload.put("email", req.getEmail());
        if (req.getFirstName() != null) payload.put("firstName", req.getFirstName());
        if (req.getLastName() != null) payload.put("lastName", req.getLastName());
        payload.put("enabled", req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        if (req.getAttributes() != null) payload.put("attributes", req.getAttributes());
        String userId = service.createUser(payload, req.getInitialPassword(), req.getTemporaryPassword() != null ? req.getTemporaryPassword() : false);
        return ResponseEntity.ok(Map.of("userId", userId));
    }

    @GetMapping("/me")
    @Operation(
        summary = "현재 토큰 기준 사용자 정보 조회",
        description = "요청의 Authorization 헤더(Bearer 토큰)로 Keycloak userinfo 엔드포인트를 호출하여 사용자 정보를 반환합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class))),
        @ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰")
    })
    public ResponseEntity<Map<String, Object>> me(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || authorization.isBlank()) {
            return ResponseEntity.status(401).body(Map.of("error", "missing Authorization header"));
        }
        Map<String, Object> info = service.getCurrentUserInfo(authorization);
        // 인트로스펙션 응답(active=false)인 경우 401로 매핑
        Object active = info.get("active");
        if (active instanceof Boolean && !((Boolean) active)) {
            return ResponseEntity.status(401).body(Map.of("error", "inactive token"));
        }
        return ResponseEntity.ok(info);
    }

    @PostMapping
    @Operation(
        summary = "관리 사용자 생성",
        description = "Keycloak에 사용자를 생성하고, 초기 비밀번호가 있으면 설정합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공 - 생성된 사용자 ID 반환",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<Map<String, String>> create(@RequestBody UserRequestDto.CreateUserReq req) {
        Map<String, Object> payload = new HashMap<>();
        if (req.getUsername() != null) payload.put("username", req.getUsername());
        if (req.getEmail() != null) payload.put("email", req.getEmail());
        if (req.getFirstName() != null) payload.put("firstName", req.getFirstName());
        if (req.getLastName() != null) payload.put("lastName", req.getLastName());
        payload.put("enabled", req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        if (req.getAttributes() != null) payload.put("attributes", req.getAttributes());
        String userId = service.createUser(payload, req.getInitialPassword(), req.getTemporaryPassword() != null ? req.getTemporaryPassword() : false);
        return ResponseEntity.ok(Map.of("userId", userId));
    }

    @PostMapping("/batch")
    @Operation(
        summary = "관리 사용자 일괄 생성",
        description = "요청 본문으로 사용자 배열을 받아 순차적으로 생성합니다. 각 항목의 성공/실패 결과를 반환합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공 - 결과 배열 반환",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Object.class))))
    })
    public ResponseEntity<List<Map<String, Object>>> createBatch(@RequestBody List<UserRequestDto.CreateUserReq> reqs) {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (UserRequestDto.CreateUserReq r : reqs) {
            Map<String, Object> m = new HashMap<>();
            if (r.getUsername() != null) m.put("username", r.getUsername());
            if (r.getEmail() != null) m.put("email", r.getEmail());
            if (r.getFirstName() != null) m.put("firstName", r.getFirstName());
            if (r.getLastName() != null) m.put("lastName", r.getLastName());
            m.put("enabled", r.getEnabled() != null ? r.getEnabled() : Boolean.TRUE);
            if (r.getAttributes() != null) m.put("attributes", r.getAttributes());
            if (r.getInitialPassword() != null) m.put("initialPassword", r.getInitialPassword());
            if (r.getTemporaryPassword() != null) m.put("temporaryPassword", r.getTemporaryPassword());
            requests.add(m);
        }
        List<Map<String, Object>> results = service.createUsersBatch(requests);
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{userId}")
    @Operation(
        summary = "관리 사용자 수정",
        description = "username, email, firstName, lastName, enabled, attributes, roleNames 필드를 수정할 수 있습니다. " +
                      "roleNames가 제공되면 사용자의 커스텀 역할도 함께 업데이트됩니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "콘텐츠 없음(성공)"),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> update(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId,
        @RequestBody UserRequestDto.UpdateUserReq req
    ) {
        // 사용자 정보 업데이트
        Map<String, Object> payload = new HashMap<>();
        if (req.getUsername() != null) payload.put("username", req.getUsername());
        if (req.getEmail() != null) payload.put("email", req.getEmail());
        if (req.getFirstName() != null) payload.put("firstName", req.getFirstName());
        if (req.getLastName() != null) payload.put("lastName", req.getLastName());
        if (req.getEnabled() != null) payload.put("enabled", req.getEnabled());
        if (req.getAttributes() != null) payload.put("attributes", req.getAttributes());
        service.updateUser(userId, payload);
        
        // 역할 업데이트 (roleNames가 제공된 경우에만)
        if (req.getRoleNames() != null) {
            service.updateUserRoles(userId, req.getRoleNames());
        }
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/password")
    @Operation(
        summary = "관리 사용자 비밀번호 초기화",
        description = "새 비밀번호와 임시 여부(temporary)를 설정합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "콘텐츠 없음(성공)"),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> resetPassword(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId,
        @RequestBody UserRequestDto.PasswordReq req
    ) {
        service.resetPassword(userId, req.getValue(), req.getTemporary() != null ? req.getTemporary() : false);
        return ResponseEntity.noContent().build();
    }

    // ===== Role Management APIs =====

    @GetMapping("/roles")
    @Operation(
        summary = "사용 가능한 역할 목록 조회",
        description = "Keycloak realm에 정의된 모든 역할 목록을 반환합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Object.class))))
    })
    public ResponseEntity<List<Map<String, Object>>> getRoles() {
        List<Map<String, Object>> roles = service.getRealmRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{userId}/roles")
    @Operation(
        summary = "사용자 역할 조회",
        description = "특정 사용자에게 할당된 realm 역할 목록을 반환합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<List<Map<String, Object>>> getUserRoles(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId
    ) {
        List<Map<String, Object>> roles = service.getUserRealmRoles(userId);
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/{userId}/roles")
    @Operation(
        summary = "사용자 역할 업데이트",
        description = "사용자의 역할을 업데이트합니다. 기존 역할을 모두 제거하고 새로운 역할 목록을 할당합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "콘텐츠 없음(성공)"),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> updateUserRoles(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId,
        @RequestBody UserRequestDto.UpdateRolesReq req
    ) {
        service.updateUserRoles(userId, req.getRoleNames() != null ? req.getRoleNames() : List.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(
        summary = "향상된 사용자 검색",
        description = "이름, 사번, 부서, 역할로 필터링하여 사용자 목록을 페이지 형식으로 조회합니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = com.ctrlf.common.dto.PageResponse.class)))
    })
    public ResponseEntity<com.ctrlf.common.dto.PageResponse<Map<String, Object>>> searchUsers(
        @Parameter(description = "이름 또는 사번 검색어", example = "김민수", required = false)
        @RequestParam(name = "search", required = false) String search,
        @Parameter(description = "부서 필터 (예: '인사팀', '개발팀')", example = "개발팀", required = false)
        @RequestParam(name = "department", required = false) String department,
        @Parameter(description = "역할 필터 (예: 'SYSTEM_ADMIN', 'EMPLOYEE')", example = "SYSTEM_ADMIN", required = false)
        @RequestParam(name = "role", required = false) String role,
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(name = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "50")
        @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        com.ctrlf.common.dto.PageResponse<Map<String, Object>> users = service.listUsersWithFilters(search, department, role, page, size);
        return ResponseEntity.ok(users);
    }

    // ===== Development helper: issue Keycloak tokens (exposed under /admin/users/token/**) =====

    private String tokenEndpoint() {
        return props.getBaseUrl() + "/realms/" + props.getRealm() + "/protocol/openid-connect/token";
    }

    @PostMapping("/token/client")
    @Operation(
        summary = "[DEV] client_credentials 토큰 발급",
        description = "keycloak.admin(client_id / client_secret)로 client_credentials 토큰을 발급합니다.",
        security = {}
    )
    public ResponseEntity<Map<String, Object>> clientCredentialsToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = restTemplate.postForObject(tokenEndpoint(), entity, Map.class);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/token/password")
    @Operation(
        summary = "[DEV] password(grant) 토큰 발급",
        description = "username/password로 토큰 발급(Direct Access Grants). clientId/Secret 미지정 시 keycloak.admin 설정 사용.",
        security = {}
    )
    public ResponseEntity<Map<String, Object>> passwordToken(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PasswordTokenRequest.class),
                examples = @ExampleObject(value = "{\n" +
                    "  \"clientId\": \"infra-admin\",\n" +
                    "  \"clientSecret\": \"changeme\",\n" +
                    "  \"username\": \"user1\",\n" +
                    "  \"password\": \"11111\",\n" +
                    "  \"scope\": \"openid profile email\"\n" +
                    "}")
            )
        )
        @RequestBody PasswordTokenRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String clientId = (request.getClientId() != null && !request.getClientId().isBlank())
            ? request.getClientId() : props.getClientId();
        String clientSecret = (request.getClientSecret() != null && !request.getClientSecret().isBlank())
            ? request.getClientSecret() : props.getClientSecret();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());
        if (request.getScope() != null && !request.getScope().isBlank()) {
            form.add("scope", request.getScope());
        }
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = restTemplate.postForObject(tokenEndpoint(), entity, Map.class);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/token/introspect")
    @Operation(
        summary = "[DEV] Service Account 토큰 인트로스펙션",
        description = "현재 설정된 클라이언트의 Service Account 토큰을 인트로스펙션하여 권한 정보를 확인합니다. " +
                      "realm_access.roles에 'realm-management' 관련 권한이 있는지 확인하세요.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공 - 토큰 정보 반환",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<Map<String, Object>> introspectServiceAccountToken() {
        Map<String, Object> tokenInfo = service.getClient().introspectServiceAccountToken();
        return ResponseEntity.ok(tokenInfo);
    }

    @GetMapping("/token/decode")
    @Operation(
        summary = "[DEV] Service Account 토큰 디코딩",
        description = "현재 설정된 클라이언트의 Service Account 토큰을 JWT로 디코딩하여 전체 payload를 확인합니다. " +
                      "인트로스펙션과 달리 토큰에 실제로 포함된 모든 클레임을 확인할 수 있습니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공 - 토큰 payload 반환",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = Object.class)))
    })
    public ResponseEntity<Map<String, Object>> decodeServiceAccountToken() {
        Map<String, Object> tokenPayload = service.getClient().decodeServiceAccountToken();
        return ResponseEntity.ok(tokenPayload);
    }
}


