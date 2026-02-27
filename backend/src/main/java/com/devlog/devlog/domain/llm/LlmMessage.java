package com.devlog.devlog.domain.llm;

import java.util.Objects;
// 컨텍스트용 메세지
public record LlmMessage(
    LlmRole role,
    String content
) {
    public LlmMessage {
        // 필드 유효성 검사후 생성자 생성
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
