package com.ctrlf.common.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 부서 enum.
 * 모든 서비스에서 공통으로 사용하는 부서 목록입니다.
 */
public enum Department {
    /** 전체 부서 (필터링용) */
    ALL("전체 부서"),
    /** 총무팀 */
    GENERAL_AFFAIRS("총무팀"),
    /** 기획팀 */
    PLANNING("기획팀"),
    /** 마케팅팀 */
    MARKETING("마케팅팀"),
    /** 인사팀 */
    HR("인사팀"),
    /** 재무팀 */
    FINANCE("재무팀"),
    /** 개발팀 */
    ENGINEERING("개발팀"),
    /** 영업팀 */
    SALES("영업팀"),
    /** 법무팀 */
    LEGAL("법무팀");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 화면에 표시할 부서명을 반환합니다.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 부서명으로 enum을 찾습니다.
     * 
     * @param displayName 부서명 (예: "인사팀")
     * @return 해당하는 Department enum, 없으면 null
     */
    public static Department fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(dept -> dept.displayName.equals(displayName))
            .findFirst()
            .orElse(null);
    }

    /**
     * UI 드롭다운 등에서 사용할 기본 표시 순서.
     */
    public static final List<Department> DEFAULT_ORDER = List.of(
        ALL,
        GENERAL_AFFAIRS,
        PLANNING,
        MARKETING,
        HR,
        FINANCE,
        ENGINEERING,
        SALES,
        LEGAL
    );

    /**
     * 부서명 리스트를 반환합니다 (UI 드롭다운용).
     */
    public static List<String> getDisplayNames() {
        return DEFAULT_ORDER.stream()
            .map(Department::getDisplayName)
            .toList();
    }
}

