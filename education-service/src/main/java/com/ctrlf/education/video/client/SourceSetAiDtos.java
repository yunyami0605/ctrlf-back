package com.ctrlf.education.video.client;

import java.util.UUID;

/**
 * 소스셋 AI 서버 호출용 DTO 모음.
 */
public final class SourceSetAiDtos {

    private SourceSetAiDtos() {}

    /**
     * 소스셋 작업 시작 요청 (v2.1).
     * 
     * <p>변경 사항:
     * <ul>
     *   <li>documents[] 제거: FastAPI가 Spring에서 조회</li>
     *   <li>datasetId, indexVersion, ingestId 제거: Spring이 미리 알 수 없음</li>
     *   <li>scriptJobId, domain, language 제거: 스펙에서 제거됨</li>
     *   <li>educationId, llmModelHint 추가: 새 스펙에 추가됨</li>
     * </ul>
     * 
     * <p>참고: FastAPI가 body를 받기 위해 필수 필드는 항상 포함하고, 선택 필드는 null일 때도 포함합니다.
     * @JsonInclude(JsonInclude.Include.NON_NULL)을 제거하여 모든 필드를 명시적으로 전송합니다.
     */
    public record StartRequest(
        /** 연결 교육 ID(선택) */
        UUID educationId,

        /** 영상 ID(백 발급, 필수) */
        String videoId,

        /** 멱등 키(권장) */
        UUID requestId,

        /** 추적용(권장) */
        String traceId,

        /** 스크립트 생성 정책 프리셋(선택) */
        String scriptPolicyId,

        /** 사용 모델 힌트(선택) */
        String llmModelHint
    ) {}

    /**
     * 소스셋 작업 시작 응답 (v2.1).
     * 
     * <p>변경 사항: scriptJobId 제거됨
     */
    public record StartResponse(
        /** 요청 수신 여부 */
        boolean received,

        /** 소스셋 ID */
        String sourceSetId,

        /** 상태 */
        String status
    ) {}
}
