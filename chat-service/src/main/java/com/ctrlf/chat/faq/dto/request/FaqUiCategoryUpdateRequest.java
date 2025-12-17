package com.ctrlf.chat.faq.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaqUiCategoryUpdateRequest {
    private String displayName;
    private Integer sortOrder;
    private Boolean isActive;
}
