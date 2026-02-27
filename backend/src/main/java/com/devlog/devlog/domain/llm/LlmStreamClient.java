package com.devlog.devlog.domain.llm;

import reactor.core.publisher.Flux;

public interface LlmStreamClient {
    Flux<LlmStreamEvent> stream(LlmRequest request);
}
