package com.devlog.devlog.config;

import com.devlog.devlog.domain.llm.LlmClient;
import com.devlog.devlog.domain.llm.LlmOptions;
import com.devlog.devlog.infra.llm.GeminiHttpClient;
import com.devlog.devlog.infra.llm.MockLlmClient;
import com.devlog.devlog.infra.llm.OllamaHttpClient;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Bean
    public RestClient geminiRestClient(
        @Value("${llm.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
        @Value("${llm.gemini.connect-timeout-ms:3000}") long connectTimeoutMs,
        @Value("${llm.gemini.read-timeout-ms:30000}") long readTimeoutMs
    ) {
        return GeminiHttpClient.buildRestClient(
            baseUrl,
            Duration.ofMillis(connectTimeoutMs),
            Duration.ofMillis(readTimeoutMs)
        );
    }

    @Bean
    public RestClient ollamaRestClient(
        @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
        @Value("${llm.ollama.connect-timeout-ms:3000}") long connectTimeoutMs,
        @Value("${llm.ollama.read-timeout-ms:30000}") long readTimeoutMs
    ) {
        return OllamaHttpClient.buildRestClient(
            baseUrl,
            Duration.ofMillis(connectTimeoutMs),
            Duration.ofMillis(readTimeoutMs)
        );
    }

    @Bean
    public LlmOptions defaultLlmOptions(
        @Value("${llm.default.temperature:0.2}") double temperature,
        @Value("${llm.default.max-tokens:65536}") int maxTokens
    ) {
        return new LlmOptions(temperature, maxTokens);
    }

    @Bean
    public LlmClient llmClient(
        @Qualifier("geminiRestClient") RestClient geminiRestClient,
        @Qualifier("ollamaRestClient") RestClient ollamaRestClient,
        LlmOptions defaultLlmOptions,
        @Value("${llm.mode:mock}") String mode,
        @Value("${llm.gemini.api-key:}") String apiKey,
        @Value("${llm.gemini.model:}") String model,
        @Value("${llm.ollama.model:}") String ollamaModel,
        @Value("${llm.mock.always-fail:false}") boolean mockAlwaysFail
    ) {
        return switch (mode.toLowerCase()) {
            case "mock" -> new MockLlmClient(mockAlwaysFail);
            case "gemini" -> new GeminiHttpClient(geminiRestClient, apiKey, model, defaultLlmOptions);
            case "ollama" -> new OllamaHttpClient(ollamaRestClient, ollamaModel, defaultLlmOptions);
            default -> throw new IllegalArgumentException("Unknown llm.mode: " + mode);
        };
    }

}
