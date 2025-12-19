package com.ctrlf.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채팅 메시지 엔티티
 * 
 * <p>사용자와 AI 간의 대화 메시지를 저장하는 엔티티입니다.</p>
 * <p>role은 "user" 또는 "assistant" 값을 가집니다.</p>
 * 
 * @author CtrlF Team
 * @since 1.0.0
 */
@Entity
@Table(name = "chat_message", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    /** 메시지 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** 세션 ID (FK) */
    @Column(name = "session_id", nullable = false, columnDefinition = "uuid")
    private UUID sessionId;

    /** 메시지 역할 ("user" 또는 "assistant") */
    @Column(nullable = false, length = 20)
    private String role;

    /** 메시지 내용 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** 입력 토큰 수 (AI 응답 시) */
    @Column(name = "tokens_in")
    private Integer tokensIn;

    /** 출력 토큰 수 (AI 응답 시) */
    @Column(name = "tokens_out")
    private Integer tokensOut;

    /** 사용된 LLM 모델명 (AI 응답 시) */
    @Column(name = "llm_model")
    private String llmModel;

    /** 메시지 생성 시각 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * 엔티티 저장 전 실행되는 콜백
     * 생성 시각을 현재 시간으로 설정합니다.
     */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * 사용자 메시지 생성 팩토리 메서드
     * 
     * @param sessionId 세션 ID
     * @param content 메시지 내용
     * @return 생성된 사용자 메시지
     */
    public static ChatMessage userMessage(UUID sessionId, String content) {
        ChatMessage m = new ChatMessage();
        m.sessionId = sessionId;
        m.role = "user";
        m.content = content;
        return m;
    }

    /**
     * AI 응답 메시지 생성 팩토리 메서드
     * 
     * <p>스트리밍 중에는 초기 content가 빈 문자열일 수 있습니다.</p>
     * 
     * @param sessionId 세션 ID
     * @param content 메시지 내용 (스트리밍 중에는 빈 문자열 가능)
     * @param tokensIn 입력 토큰 수
     * @param tokensOut 출력 토큰 수
     * @param llmModel 사용된 LLM 모델명
     * @return 생성된 AI 응답 메시지
     */
    public static ChatMessage assistantMessage(
        UUID sessionId,
        String content,
        Integer tokensIn,
        Integer tokensOut,
        String llmModel
    ) {
        ChatMessage m = new ChatMessage();
        m.sessionId = sessionId;
        m.role = "assistant";
        m.content = content;
        m.tokensIn = tokensIn;
        m.tokensOut = tokensOut;
        m.llmModel = llmModel;
        return m;
    }

    /**
     * 메시지 내용 업데이트
     * 
     * <p>스트리밍 완료 후 최종 답변을 업데이트할 때 사용합니다.</p>
     * 
     * @param content 업데이트할 메시지 내용
     */
    public void updateContent(String content) {
        this.content = content;
    }
}
