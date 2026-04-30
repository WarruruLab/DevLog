package com.devlog.devlog.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.devlog.devlog.domain.llm.LlmFinishReason;
import com.devlog.devlog.domain.llm.LlmMessage;
import com.devlog.devlog.domain.llm.LlmOptions;
import com.devlog.devlog.domain.llm.LlmRequest;
import com.devlog.devlog.domain.llm.LlmResult;
import com.devlog.devlog.domain.llm.LlmRole;
import com.devlog.devlog.domain.llm.LlmTokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OllamaHttpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsRequestToOllamaChatPayload() {
        LlmRequest request = new LlmRequest(
            "system prompt",
            List.of(
                new LlmMessage(LlmRole.SYSTEM, "system message"),
                new LlmMessage(LlmRole.USER, "hello"),
                new LlmMessage(LlmRole.AI, "hi")
            ),
            new LlmOptions(0.7, 128)
        );

        OllamaHttpClient.OllamaChatRequest payload =
            OllamaHttpClient.OllamaChatRequest.from(request, "llama3.2", false, new LlmOptions(0.2, 64));

        assertThat(payload.model()).isEqualTo("llama3.2");
        assertThat(payload.stream()).isFalse();
        assertThat(payload.messages())
            .extracting(OllamaHttpClient.OllamaChatRequest.Message::role)
            .containsExactly("system", "system", "user", "assistant");
        assertThat(payload.options()).containsEntry("temperature", 0.7);
        assertThat(payload.options()).containsEntry("num_predict", 128);
    }

    @Test
    void extractsTextAndTokenUsageFromOllamaResponse() throws Exception {
        var response = objectMapper.readTree("""
            {
              "message": {"role": "assistant", "content": " answer "},
              "done_reason": "stop",
              "prompt_eval_count": 11,
              "eval_count": 22
            }
            """);

        assertThat(OllamaHttpClient.extractText(response)).isEqualTo("answer");
        assertThat(OllamaHttpClient.mapFinishReason("stop")).isEqualTo(LlmFinishReason.STOP);
        assertThat(OllamaHttpClient.toTokenUsage(response))
            .isEqualTo(new LlmTokenUsage(11, 22));
    }

    @Test
    void successUsesEmptyTokenUsageWhenNull() {
        LlmResult.Success success = new LlmResult.Success("ok", null, null);

        assertThat(success.finishReason()).isEqualTo(LlmFinishReason.UNKNOWN);
        assertThat(success.tokenUsage()).isEqualTo(LlmTokenUsage.empty());
    }
}
