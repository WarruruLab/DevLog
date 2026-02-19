package com.devlog.devlog.domain.llm;

public record LlmTokenUsage(
    int inputTokenCount,     // 질문 토큰 수
    int outputTokenCount  // 답변 토큰 수
) {
    // 빈 객체 생성 NPE를 방지하기 위함
    public static LlmTokenUsage empty() {
        return new LlmTokenUsage(0, 0);
    }

    public int totalToken(){
        return this.inputTokenCount + outputTokenCount;
    }
}
