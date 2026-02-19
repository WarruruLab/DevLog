package com.devlog.devlog.domain.llm;

public record LlmStreamEvent(String delta, LlmFinishReason finishReason, LlmTokenUsage tokenUsage) {
    public static LlmStreamEvent delta(String d) { return new LlmStreamEvent(d, LlmFinishReason.UNKNOWN, null); }
    public static LlmStreamEvent done(LlmFinishReason r) { return new LlmStreamEvent("", r, null); }
    public static LlmStreamEvent finish(LlmFinishReason r, LlmTokenUsage usage) {return new LlmStreamEvent("", r, usage);}
}
