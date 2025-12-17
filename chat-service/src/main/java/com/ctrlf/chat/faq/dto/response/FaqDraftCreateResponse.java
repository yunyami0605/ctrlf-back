package com.ctrlf.chat.faq.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FaqDraftCreateResponse {

    private UUID draftId;
}
