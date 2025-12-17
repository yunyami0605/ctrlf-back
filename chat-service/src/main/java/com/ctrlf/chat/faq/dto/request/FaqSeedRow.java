package com.ctrlf.chat.faq.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * CSV 한 줄 = FAQ 한 건
 */
@Getter
@Setter
public class FaqSeedRow {

    private String domain;
    private String question;
    private String answerMarkdown;
    private String summary;
}
