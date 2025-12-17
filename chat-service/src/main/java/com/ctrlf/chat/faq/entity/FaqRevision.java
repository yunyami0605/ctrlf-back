package com.ctrlf.chat.faq.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * FAQ 변경 이력 관리 엔티티
 */
@Entity
@Table(name = "faq_revisions", schema = "chat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqRevision {

    @Id
    @GeneratedValue
    private UUID id;

    private String targetType;
    private UUID targetId;
    private String action;
    private UUID actorId;
    private String reason;
    private Instant createdAt;

    public static FaqRevision create(
        String targetType,
        UUID targetId,
        String action,
        UUID actorId,
        String reason
    ) {
        FaqRevision revision = new FaqRevision();
        revision.targetType = targetType;
        revision.targetId = targetId;
        revision.action = action;
        revision.actorId = actorId;
        revision.reason = reason;
        revision.createdAt = Instant.now();
        return revision;
    }
}
