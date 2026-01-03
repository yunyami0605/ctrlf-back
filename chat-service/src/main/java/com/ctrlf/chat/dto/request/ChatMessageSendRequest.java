package com.ctrlf.chat.dto.request;

import java.util.UUID;

/**
 * 메시지 전송 요청 DTO. (섹션 삭제 반영)
 */
public record ChatMessageSendRequest(
    /** 대상 세션 ID */
    UUID sessionId,
    /** 메시지 내용 */
    String content,
    /**
     * A/B 테스트 임베딩 모델 선택 (선택)
     * - "openai": text-embedding-3-large (기본값)
     * - "sroberta": ko-sroberta-multitask
     * - null: 기본값(openai) 사용
     */
    String model
) {}
