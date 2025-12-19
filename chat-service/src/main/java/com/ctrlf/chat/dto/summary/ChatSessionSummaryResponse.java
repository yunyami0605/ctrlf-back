package com.ctrlf.chat.dto.summary;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatSessionSummaryResponse {

    private String summary;
    private String intent;
    private List<String> keywords;

    public static ChatSessionSummaryResponse empty() {
        ChatSessionSummaryResponse res = new ChatSessionSummaryResponse();
        res.summary = null;
        res.intent = null;
        res.keywords = List.of();
        return res;
    }
}
