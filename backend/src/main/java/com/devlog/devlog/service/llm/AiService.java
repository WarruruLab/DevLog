package com.devlog.devlog.service.llm;

import com.devlog.devlog.domain.llm.LlmClient;
import com.devlog.devlog.domain.llm.LlmMessage;
import com.devlog.devlog.domain.llm.LlmOptions;
import com.devlog.devlog.domain.llm.LlmRequest;
import com.devlog.devlog.domain.llm.LlmResult;
import com.devlog.devlog.domain.llm.LlmRole;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final LlmClient llmClient;

    public AiService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 기본 호출 메서드
     */
    public String ask(String systemPrompt, String userPrompt) {
        LlmRequest request = new LlmRequest(
            systemPrompt,
            List.of(new LlmMessage(LlmRole.USER, userPrompt)),
            LlmOptions.defaults()
        );

        return execute(request);
    }

    /**
     * 상세 요청 처리
     */
    public String ask(LlmRequest request) {
        return execute(request);
    }

    /**
     * 상우님이 작성하신 LlmResult 구조에 맞춘 결과 처리
     */
    private String execute(LlmRequest request) {
        LlmResult result = llmClient.generate(request);

        return switch (result) {
            case LlmResult.Success success -> success.text();

            // Failure 처리
            case LlmResult.Failure failure ->
                throw new RuntimeException("AI 분석 실패: " + failure.message());
        };
    }
}
