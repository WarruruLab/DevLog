package com.devlog.devlog.service.llm;

import com.devlog.devlog.domain.llm.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final LlmClient llmClient;

    public AiService(LlmClient llmClient){
        this.llmClient = llmClient;
    }
}
