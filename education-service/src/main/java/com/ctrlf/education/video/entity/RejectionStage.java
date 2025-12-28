package com.ctrlf.education.video.entity;

/**
 * 영상 반려 단계 enum.
 * 어떤 검토 단계에서 반려되었는지를 나타냅니다.
 */
public enum RejectionStage {
    /** 스크립트 검토 단계에서 반려 (1차 반려) */
    SCRIPT,
    
    /** 영상 검토 단계에서 반려 (2차 반려) */
    VIDEO
}

