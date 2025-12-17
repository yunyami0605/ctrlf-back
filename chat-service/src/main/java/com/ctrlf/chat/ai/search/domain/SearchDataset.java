package com.ctrlf.chat.ai.search.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * AI 검색 대상 데이터셋 정의
 */
@Getter
@RequiredArgsConstructor
public enum SearchDataset {

    POLICY("policy"),
    TRAINING("training"),
    INCIDENT("incident"),
    SECURITY("security"),
    EDUCATION("education");

    private final String value;

    /**
     * 문자열을 SearchDataset으로 변환
     */
    public static SearchDataset from(String value) {
        return Arrays.stream(values())
            .filter(d -> d.value.equals(value))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("Invalid dataset: " + value)
            );
    }
}
