package com.ctrlf.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_session", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {

    /** 채팅 세션(채팅방) PK */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    /** 세션 생성 사용자 UUID */
    @Column(name = "user_uuid", columnDefinition = "uuid")
    private UUID userUuid;

    /** 세션 제목 */
    @Column(name = "title")
    private String title;

    /** 업무 도메인(FAQ/보안/직무/상담 등) */
    @Column(name = "domain")
    private String domain;

    /** 세션 요약 (AI 생성) */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    /** 세션 의도 분류 */
    @Column(name = "intent", length = 50)
    private String intent;

    /** 세션 생성 시각 */
    @Column(name = "created_at")
    private Instant createdAt;

    /** 마지막 메시지 업데이트 시각 */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** 삭제 플래그 */
    @Column(name = "deleted")
    private Boolean deleted;

    /**
     * 엔티티 저장 전 실행되는 콜백
     * 생성 시각, 수정 시각을 현재 시간으로 설정하고 삭제 플래그를 false로 초기화합니다.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.deleted = false;
    }

    /**
     * 엔티티 수정 전 실행되는 콜백
     * 수정 시각을 현재 시간으로 업데이트합니다.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 세션 제목 업데이트
     * 
     * @param title 새로운 제목
     */
    public void updateTitle(String title) {
        this.title = title;
    }

    /**
     * 세션 소프트 삭제
     * 
     * <p>실제로 데이터를 삭제하지 않고 deleted 플래그를 true로 설정합니다.</p>
     */
    public void softDelete() {
        this.deleted = true;
    }
}
