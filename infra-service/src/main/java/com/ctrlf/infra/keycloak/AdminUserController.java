package com.ctrlf.infra.keycloak;

import com.ctrlf.infra.keycloak.dto.UserRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public AdminUserController(KeycloakAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
        summary = "관리 사용자 목록 조회",
        description = "Keycloak Admin API의 /users 결과를 그대로 반환합니다. 페이징 메타데이터는 포함되지 않습니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = Object.class))))
    })
    public ResponseEntity<List<Map<String, Object>>> list(
        @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
        @RequestParam(name = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기(최대 200)", example = "50")
        @RequestParam(name = "size", defaultValue = "50") int size,
        @Parameter(description = "사용자 검색어(username, email 등)", example = "jane", required = false)
        @RequestParam(name = "search", required = false) String search
    ) {
        List<Map<String, Object>> users = service.listUsers(search, page, size);
        return ResponseEntity.ok(users);
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
        description = "username, email, firstName, lastName, enabled, attributes 필드만 반영됩니다.",
        security = {}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "콘텐츠 없음(성공)"),
        @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> update(
        @Parameter(description = "Keycloak 사용자 ID", example = "c2f0a1c3-....")
        @PathVariable String userId,
        @RequestBody Map<String, Object> body
    ) {
        Map<String, Object> payload = new HashMap<>();
        // pass-through safe fields
        for (String k : List.of("username", "email", "firstName", "lastName", "enabled", "attributes")) {
            if (body.containsKey(k)) {
                payload.put(k, body.get(k));
            }
        }
        service.updateUser(userId, payload);
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
}


