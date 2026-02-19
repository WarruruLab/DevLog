package com.devlog.devlog.domain.llm;

import java.util.List;
import java.util.Objects;

public record LlmRequest(
    String systemPrompt,
    List<LlmMessage> messages,
    LlmOptions options
) {
    public LlmRequest {
        // 필드 유효성 검사후에 생성자 생성
        Objects.requireNonNull(systemPrompt, "prompt must not be null");
        Objects.requireNonNull(messages, "messages must not be null");
    }

    public static LlmRequest of(String systemPrompt, List<LlmMessage> messages){
        return new LlmRequest(systemPrompt, messages, null);
    }
}
