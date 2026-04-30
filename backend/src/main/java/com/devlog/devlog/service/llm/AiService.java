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
    private final LlmOptions defaultOptions;

    public AiService(LlmClient llmClient, LlmOptions defaultOptions) {
        this.llmClient = llmClient;
        this.defaultOptions = defaultOptions;
    }

    /**
     * 기본 호출 메서드
     */
    public String ask(String systemPrompt, String userPrompt) {
        LlmRequest request = new LlmRequest(
            systemPrompt,
            List.of(new LlmMessage(LlmRole.USER, userPrompt)),
            defaultOptions
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
        LlmResult result = llmClient.generate(applyDefaultOptions(request));

        return switch (result) {
            case LlmResult.Success success -> success.text();

            // Failure 처리
            case LlmResult.Failure failure ->
                throw new RuntimeException("AI 분석 실패: " + failure.message());
        };
    }

    private LlmRequest applyDefaultOptions(LlmRequest request) {
        if (request.options() != null) {
            return request;
        }
        return new LlmRequest(request.systemPrompt(), request.messages(), defaultOptions);
    }
}
