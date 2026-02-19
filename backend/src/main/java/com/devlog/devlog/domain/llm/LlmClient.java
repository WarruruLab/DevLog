package com.devlog.devlog.domain.llm;

public interface LlmClient {
    LlmResult generate(LlmRequest request);
}
