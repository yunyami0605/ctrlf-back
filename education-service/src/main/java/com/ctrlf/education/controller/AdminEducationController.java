package com.ctrlf.education.controller;

import com.ctrlf.common.dto.MutationResponse;
import com.ctrlf.education.dto.EducationRequests.CreateEducationRequest;
import com.ctrlf.education.dto.EducationRequests.UpdateEducationRequest;
import com.ctrlf.education.dto.EducationResponses;
import com.ctrlf.education.dto.EducationResponses.EducationDetailResponse;
import com.ctrlf.education.dto.EducationResponses.EducationVideosResponse;
import com.ctrlf.education.service.EducationService;
import com.ctrlf.education.entity.Education;
import com.ctrlf.education.repository.EducationRepository;
import com.ctrlf.education.video.dto.VideoDtos.VideoStatus;
import com.ctrlf.education.video.entity.EducationVideo;
import com.ctrlf.education.video.repository.EducationVideoRepository;
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
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
// import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 교육 리소스 어드민 전용 컨트롤러.
 * - 생성/상세/수정/삭제 등 관리 기능 제공
 */
@RestController
@Tag(name = "Education-Admin", description = "교육 리소스 관리 API (ADMIN)")
@SecurityRequirement(name = "bearer-jwt")
@RequestMapping("/admin")
// @PreAuthorize("hasRole('SYSTEM_ADMIN')") 추후 추가
public class AdminEducationController {

    private final EducationService educationService;
    private final EducationRepository educationRepository;
    private final EducationVideoRepository educationVideoRepository;

    public AdminEducationController(
        EducationService educationService,
        EducationRepository educationRepository,
        EducationVideoRepository educationVideoRepository
    ) {
        this.educationService = educationService;
        this.educationRepository = educationRepository;
        this.educationVideoRepository = educationVideoRepository;
    }

    @PostMapping("/edu")
    @Operation(
        summary = "교육 생성(* 개발용)",
        description = "교육을 생성합니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CreateEducationRequest.class),
                examples = @ExampleObject(
                    value = "{\n" +
                            "  \"title\": \"산업안전 교육\",\n" +
                            "  \"description\": \"산업안전 수칙\",\n" +
                            "  \"category\": \"JOB_DUTY\",\n" +
                            "  \"eduType\": \"MANDATORY\",\n" +
                            "  \"passScore\": 80,\n" +
                            "  \"passRatio\": 90,\n" +
                            "  \"require\": true\n" +
                            "}"
                )
            )
        ),
        responses = {
            @ApiResponse(
                responseCode = "201",
                description = "생성됨",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = "{ \"id\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\" }"
                    )
                )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성됨"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    public ResponseEntity<MutationResponse<UUID>> createEducation(@jakarta.validation.Valid @RequestBody CreateEducationRequest req) {
        MutationResponse<UUID> res = educationService.createEducation(req);
        return ResponseEntity
            .created(URI.create("/admin/edu/" + res.getId()))
            .body(res);
    }

    @GetMapping("/edu/{id}")
    @Operation(
        summary = "교육 상세 조회(* 개발용)",
        description = "교육 기본 정보와 차시 정보를 조회합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = EducationDetailResponse.class),
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"id\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\",\n" +
                                "  \"title\": \"산업안전 교육\",\n" +
                                "  \"description\": \"산업안전 수칙\",\n" +
                                "  \"category\": \"JOB_DUTY\",\n" +
                                "  \"eduType\": \"MANDATORY\",\n" +
                                "  \"require\": true,\n" +
                                "  \"passScore\": 80,\n" +
                                "  \"passRatio\": 90,\n" +
                                "  \"duration\": 3600,\n" +
                                "  \"createdAt\": \"2025-12-17T10:00:00Z\",\n" +
                                "  \"updatedAt\": \"2025-12-17T10:00:00Z\",\n" +
                                "  \"sections\": []\n" +
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
    public ResponseEntity<EducationDetailResponse> getEducationDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(educationService.getEducationDetail(id));
    }

    @PutMapping("/edu/{id}")
    @Operation(
        summary = "교육 수정(* 개발용)",
        description = "교육 정보를 부분 업데이트합니다.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UpdateEducationRequest.class),
                examples = @ExampleObject(
                    value = "{\n" +
                            "  \"title\": \"산업안전 교육(개정)\",\n" +
                            "  \"description\": \"수칙 개정\",\n" +
                            "  \"category\": \"JOB_DUTY\",\n" +
                            "  \"eduType\": \"MANDATORY\",\n" +
                            "  \"passScore\": 85,\n" +
                            "  \"passRatio\": 95,\n" +
                            "  \"require\": true\n" +
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
                    examples = @ExampleObject(
                        value = "{\n" +
                                "  \"eduId\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\",\n" +
                                "  \"updated\": true,\n" +
                                "  \"updatedAt\": \"2025-12-17T10:00:00Z\"\n" +
                                "}"
                    )
                )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "교육을 찾을 수 없음")
        }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "404", description = "교육을 찾을 수 없음")
    })
    public ResponseEntity<Map<String, Object>> updateEducation(
        @PathVariable("id") UUID id,
        @RequestBody UpdateEducationRequest req
    ) {
        Instant updatedAt = educationService.updateEducation(id, req);
        return ResponseEntity.ok(Map.of("eduId", id, "updated", true, "updatedAt", updatedAt.toString()));
    }

    @DeleteMapping("/edu/{id}")
    @Operation(
        summary = "교육 삭제(* 개발용)",
        description = "교육을 삭제합니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                        value = "{ \"eduId\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\", \"status\": \"DELETED\" }"
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
    public ResponseEntity<Map<String, Object>> deleteEducation(@PathVariable("id") UUID id) {
        educationService.deleteEducation(id);
        return ResponseEntity.ok(Map.of("eduId", id, "status", "DELETED"));
    }

    /**
     * 전체 교육과 하위 영상 목록 조회(ADMIN).
     * 사용자 진행 정보 없이 메타만 제공합니다.
     */
    @GetMapping("/edus/with-videos")
    @Operation(
        summary = "전체 교육 + 영상 목록 조회 (프론트 -> 백)",
        description = "모든 교육을 조회하고 각 교육에 포함된 영상 목록을 함께 반환합니다(사용자 진행 정보 제외). " +
                      "status 파라미터로 특정 상태의 영상만 필터링할 수 있습니다.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "성공",
                content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = EducationVideosResponse.class)),
                    examples = @ExampleObject(
                        value = "[{\n" +
                                "  \"id\": \"2c2f8c7a-8a2c-4f3a-9d2b-111111111111\",\n" +
                                "  \"title\": \"산업안전 교육\",\n" +
                                "  \"videos\": [\n" +
                                "    {\n" +
                                "      \"id\": \"3d3f8c7a-8a2c-4f3a-9d2b-222222222222\",\n" +
                                "      \"fileUrl\": \"https://cdn.example.com/video1.mp4\",\n" +
                                "      \"duration\": 1800,\n" +
                                "      \"version\": 1,\n" +
                                "      \"isMain\": true\n" +
                                "    }\n" +
                                "  ]\n" +
                                "}]"
                    )
                )
            )
        }
    )
    public ResponseEntity<List<EducationVideosResponse>> getAllEducationsWithVideos(
            @RequestParam(value = "status", required = false) VideoStatus status) {
        List<Education> edus = educationRepository.findAll();
        List<EducationVideosResponse> result = new ArrayList<>();
        for (Education e : edus) {
            List<EducationVideo> videos;
            if (status != null) {
                // status 필터 적용
                videos = educationVideoRepository.findByEducationIdAndStatusOrderByOrderIndexAscCreatedAtAsc(
                    e.getId(), status.name());
            } else {
                // 모든 영상 조회
                videos = educationVideoRepository.findByEducationIdOrderByOrderIndexAscCreatedAtAsc(e.getId());
            }
            List<EducationVideosResponse.VideoItem> items = new ArrayList<>();
            for (EducationVideo v : videos) {
                items.add(new EducationVideosResponse.VideoItem(
                    v.getId(),
                    v.getTitle(),
                    v.getFileUrl(),
                    v.getDuration() != null ? v.getDuration() : 0,
                    v.getVersion() != null ? v.getVersion() : 1,
                    v.getDepartmentScope(),
                    null, // resumePosition
                    null, // isCompleted
                    null, // totalWatchSeconds
                    null, // progressPercent
                    null  // watchStatus
                ));
            }
            result.add(EducationVideosResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .videos(items)
                .build());
        }
        return ResponseEntity.ok(result);
    }

    // ========================
    // 대시보드 통계 API
    // ========================

    @GetMapping("/dashboard/education/summary")
    @Operation(
        summary = "대시보드 요약 통계 조회",
        description = "전체 평균 이수율, 미이수자 수, 4대 의무교육 평균, 직무교육 평균을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = EducationResponses.DashboardSummaryResponse.class)))
    })
    public ResponseEntity<EducationResponses.DashboardSummaryResponse> getDashboardSummary(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(educationService.getDashboardSummary(period, department));
    }

    @GetMapping("/dashboard/education/mandatory-completion")
    @Operation(
        summary = "4대 의무교육 이수율 조회",
        description = "성희롱 예방교육, 개인정보보호 교육, 직장 내 괴롭힘 예방, 장애인 인식개선 교육의 이수율을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = EducationResponses.MandatoryCompletionResponse.class)))
    })
    public ResponseEntity<EducationResponses.MandatoryCompletionResponse> getMandatoryCompletion(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(educationService.getMandatoryCompletion(period, department));
    }

    @GetMapping("/dashboard/education/job-completion")
    @Operation(
        summary = "직무교육 이수 현황 조회",
        description = "직무교육별 상태(진행 중/이수 완료)와 학습자 수를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = EducationResponses.JobEducationCompletionResponse.class)))
    })
    public ResponseEntity<EducationResponses.JobEducationCompletionResponse> getJobEducationCompletion(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period,
        @Parameter(description = "부서 필터", example = "총무팀")
        @RequestParam(value = "department", required = false) String department) {
        return ResponseEntity.ok(educationService.getJobEducationCompletion(period, department));
    }

    @GetMapping("/dashboard/education/department-completion")
    @Operation(
        summary = "부서별 이수율 현황 조회",
        description = "부서별 대상자 수, 이수자 수, 이수율, 미이수자 수를 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = EducationResponses.DepartmentCompletionResponse.class)))
    })
    public ResponseEntity<EducationResponses.DepartmentCompletionResponse> getDepartmentCompletion(
        @Parameter(description = "기간 (일수, 7/30/90)", example = "30")
        @RequestParam(value = "period", required = false) Integer period) {
        return ResponseEntity.ok(educationService.getDepartmentCompletion(period));
    }
}

