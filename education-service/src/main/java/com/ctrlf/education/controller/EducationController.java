package com.ctrlf.education.controller;

import com.ctrlf.education.dto.EducationRequests.VideoProgressUpdateRequest;
import com.ctrlf.education.dto.EducationResponses.EducationVideosResponse;
import com.ctrlf.education.dto.EducationResponses.VideoProgressResponse;
import com.ctrlf.education.dto.EducationResponses;

import com.ctrlf.education.service.EducationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ctrlf.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
/**
 * 교육 도메인 REST 컨트롤러.
 * <p>
 * - 목록/상세/수정/삭제 등 교육 리소스 CRUD와
 * <p>
 * - 영상 목록 및 진행률(수강) 업데이트, 교육 이수 처리 API를 제공합니다.
 * <p>
 * JWT 값을 사용자 UUID로 해석하여 사용자별 진행 정보를 처리합니다.
 */
@RestController
@Tag(name = "Education", description = "교육 리소스 API")
@SecurityRequirement(name = "bearer-jwt")
@RequiredArgsConstructor
@RequestMapping
public class EducationController {

    private final EducationService educationService;

    /**
     * 교육 및 영상 목록(내 목록)
     *
     * @param completed 이수 여부(옵션)
     * @param category 카테고리(옵션, 예: JOB/MANDATORY/ETC)
     * @param sort 정렬 기준(UPDATED|TITLE)
     * @param jwt 인증 토큰
     * @return 교육 목록(영상 목록 포함)
     */
    @GetMapping("/edus/me")
    @Operation(
        summary = "사용자 자신 교육 및 영상 목록 조회 (프론트 -> 백엔드)",
        description = "로그인 사용자 기준 교육 목록과 각 교육의 영상 목록/진행 정보를 반환합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = EducationResponses.EducationListItem.class)),
                    examples = @ExampleObject(
                        name = "ok",
                        value = "[{\n" +
                                "  \"id\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\",\n" +
                                "  \"title\": \"산업안전 교육\",\n" +
                                "  \"description\": \"산업안전 수칙\",\n" +
                                "  \"category\": \"JOB_DUTY\",\n" +
                                "  \"eduType\": \"MANDATORY\",\n" +
                                "  \"required\": true,\n" +
                                "  \"progressPercent\": 60,\n" +
                                "  \"watchStatus\": \"시청중\",\n" +
                                "  \"videos\": [\n" +
                                "    {\n" +
                                "      \"id\": \"3d3f8c7a-8a2c-4f3a-9d2b-222222222222\",\n" +
                                "      \"title\": \"2024년 성희롱 예방 교육\",\n" +
                                "      \"fileUrl\": \"https://cdn.example.com/video1.mp4\",\n" +
                                "      \"duration\": 1800,\n" +
                                "      \"version\": 1,\n" +
                                "      \"departmentScope\": \"[\\\"개발팀\\\",\\\"인사팀\\\"]\",\n" +
                                "      \"resumePosition\": 600,\n" +
                                "      \"isCompleted\": false,\n" +
                                "      \"totalWatchSeconds\": 600,\n" +
                                "      \"progressPercent\": 33,\n" +
                                "      \"watchStatus\": \"시청중\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}]"
                    )
                )
            )
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공")
    })
    public ResponseEntity<List<EducationResponses.EducationListItem>> getEducationsMe(
        @Parameter(description = "이수 여부 필터") @RequestParam(name = "completed", required = false) Boolean completed,
        @Parameter(description = "교육 유형 필터(MANDATORY/JOB/ETC)") @RequestParam(name = "eduType", required = false) String eduType,
        @Parameter(description = "정렬 기준(UPDATED|TITLE)") @RequestParam(name = "sort", required = false, defaultValue = "UPDATED") String sort,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Optional<UUID> userUuid = SecurityUtils.extractUserUuid(jwt);
        List<EducationResponses.EducationListItem> res =
            educationService.getEducationsMe(completed, eduType, sort, userUuid);
        return ResponseEntity.ok(res);
    }


    /**
     * 교육 영상 목록 및 사용자별 진행 정보 조회.
     *
     * @param id 교육 ID
     * @param jwt 인증 토큰
     * @return 영상 목록과 진행 정보
     */
    @GetMapping("/edu/{id}/videos")
    @Operation(
        summary = "교육 영상 목록 조회 (프론트 -> 백엔드)",
        description = "교육에 포함된 영상 목록과 사용자별 진행 정보를 조회합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = EducationVideosResponse.class),
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"id\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\",\n" +
                                "  \"title\": \"산업안전 교육\",\n" +
                                "  \"videos\": [\n" +
                                "    {\n" +
                                "      \"id\": \"3d3f8c7a-8a2c-4f3a-9d2b-222222222222\",\n" +
                                "      \"title\": \"2024년 성희롱 예방 교육\",\n" +
                                "      \"fileUrl\": \"https://cdn.example.com/video1.mp4\",\n" +
                                "      \"duration\": 1800,\n" +
                                "      \"version\": 1,\n" +
                                "      \"departmentScope\": \"[\\\"개발팀\\\",\\\"인사팀\\\"]\",\n" +
                                "      \"resumePosition\": 600,\n" +
                                "      \"isCompleted\": false,\n" +
                                "      \"totalWatchSeconds\": 600,\n" +
                                "      \"progressPercent\": 33,\n" +
                                "      \"watchStatus\": \"시청중\"\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}"
                    )
                )
            ),
            @ApiResponse(responseCode = "404", description = "교육을 찾을 수 없음")
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "404", description = "교육을 찾을 수 없음")
    })
    public ResponseEntity<EducationVideosResponse> getEducationVideos(
        @Parameter(description = "교육 ID") @PathVariable("id") UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Optional<UUID> userUuid = SecurityUtils.extractUserUuid(jwt);
        List<String> userDepartments = SecurityUtils.extractDepartments(jwt);
        return ResponseEntity.ok(educationService.getEducationVideos(id, userUuid, userDepartments));
    }

    /**
     * 영상 시청 진행률 업데이트. (교육의 모든 영상이 시청이 완료되면 자동으로 교육 시청 완료 처리됩니다.)
     *
     * @param educationId 교육 ID
     * @param videoId 영상 ID
     * @param jwt 인증 토큰(사용자 UUID 파싱)
     * @param req 진행률/시청시간 정보
     * @return 업데이트 결과 요약
     */
    @PostMapping("/edu/{educationId}/video/{videoId}/progress")
    @Operation(
        summary = "영상 시청 진행률 업데이트 (프론트 -> 백엔드)",
        description = "특정 교육의 특정 영상에 대한 사용자 시청 진행 정보를 업데이트합니다. 교육의 모든 영상이 시청이 완료되면 자동으로 교육 시청 완료 처리됩니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = VideoProgressUpdateRequest.class),
                examples = @ExampleObject(
                    value = "{\n" +
                            "  \"position\": 120,\n" +
                            "  \"watchTime\": 120\n" +
                            "}"
                )
            )
        ),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = VideoProgressResponse.class),
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"updated\": true,\n" +
                                "  \"progress\": 7,\n" +
                                "  \"isCompleted\": false,\n" +
                                "  \"totalWatchSeconds\": 120,\n" +
                                "  \"eduProgress\": 40,\n" +
                                "  \"eduCompleted\": false\n" +
                                "}"
                    )
                )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음")
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음")
    })
    public ResponseEntity<VideoProgressResponse> updateVideoProgress(
        @Parameter(description = "교육 ID") @PathVariable UUID educationId,
        @Parameter(description = "영상 ID") @PathVariable UUID videoId,
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody VideoProgressUpdateRequest req
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt).orElse(null);
        return ResponseEntity.ok(educationService.updateVideoProgress(educationId, videoId, userUuid, req));
    }

    /**
     * 교육 시청 완료 처리.
     *
     * @param id 교육 ID
     * @param jwt 인증 토큰(사용자 UUID 파싱)
     * @return 교육 시청완료 처리 결과
     */
    @PostMapping("/edu/{id}/complete")
    @Operation(
        summary = "교육 시청 완료 처리 (프론트 -> 백엔드, 필요하면 사용하기)",
        description = "교육 시청 완료 처리를 수행합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "이수 완료",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"status\": \"COMPLETED\",\n" +
                                "  \"completedAt\": \"2025-12-17T10:00:00Z\"\n" +
                                "}"
                    )
                )
            ),
            @ApiResponse(
                responseCode = "400",
                description = "이수 조건 미충족",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"status\": \"FAILED\",\n" +
                                "  \"message\": \"영상 이수 조건 미충족\"\n" +
                                "}"
                    )
                )
            )
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이수 완료"),
        @ApiResponse(responseCode = "400", description = "이수 조건 미충족 또는 잘못된 요청")
    })
    public ResponseEntity<Map<String, Object>> completeEducation(
        @Parameter(description = "교육 ID") @PathVariable("id") UUID id,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userUuid = SecurityUtils.extractUserUuid(jwt).orElse(null);
        Map<String, Object> body = educationService.completeEducation(id, userUuid);
        boolean ok = "COMPLETED".equals(body.get("status"));
        return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(body);
    }
}

