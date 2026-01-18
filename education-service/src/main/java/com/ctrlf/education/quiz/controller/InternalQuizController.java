package com.ctrlf.education.quiz.controller;

import com.ctrlf.education.entity.EducationTopic;
import com.ctrlf.education.quiz.dto.QuizResponse.TopicScoreResponse;
import com.ctrlf.education.quiz.service.InternalQuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 퀴즈 내부 API 컨트롤러 (infra-service ↔ education-service).
 */
@Tag(name = "Internal - Quiz", description = "퀴즈 내부 API (Personalization용)")
@RestController
@RequestMapping("/internal/quiz")
@SecurityRequirement(name = "internal-token")
@RequiredArgsConstructor
@Slf4j
public class InternalQuizController {

    private final InternalQuizService internalQuizService;

    /**
     * 사용자의 특정 토픽 퀴즈 점수를 조회합니다 (Q7, Q18용).
     *
     * @param userId 사용자 UUID (X-User-Id 헤더)
     * @param topic 교육 토픽 (WORKPLACE_BULLYING, SEXUAL_HARASSMENT_PREVENTION 등)
     * @return 해당 토픽 퀴즈 점수 정보
     */
    @Operation(summary = "토픽별 퀴즈 점수 조회 (Q7, Q18)",
        description = "사용자의 특정 토픽 퀴즈 점수를 조회합니다. 교육별 최고 점수를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = TopicScoreResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "내부 토큰 오류")
    })
    @GetMapping("/score-by-topic")
    public ResponseEntity<TopicScoreResponse> getScoreByTopic(
        @Parameter(description = "사용자 UUID", required = true)
        @RequestHeader("X-User-Id") String userId,
        @Parameter(description = "교육 토픽", required = true)
        @RequestParam("topic") String topic
    ) {
        UUID userUuid = UUID.fromString(userId);
        EducationTopic educationTopic;

        try {
            educationTopic = EducationTopic.valueOf(topic.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid topic: {}", topic);
            return ResponseEntity.badRequest().build();
        }

        TopicScoreResponse response = internalQuizService.getScoreByTopic(userUuid, educationTopic);
        return ResponseEntity.ok(response);
    }
}
