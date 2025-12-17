package com.ctrlf.chat.faq.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaqUiCategoryCreateRequest {
    private String slug;
    private String displayName;
    private Integer sortOrder;
}
