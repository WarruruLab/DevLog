package com.devlog.devlog.config;

import com.devlog.devlog.domain.llm.LlmClient;
import com.devlog.devlog.domain.llm.LlmOptions;
import com.devlog.devlog.infra.llm.GeminiHttpClient;
import com.devlog.devlog.infra.llm.MockLlmClient;

import java.time.Duration;
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
    public LlmOptions defaultLlmOptions(
        @Value("${llm.default.temperature:0.2}") double temperature,
        @Value("${llm.default.max-tokens:65536}") int maxTokens
    ) {
        return new LlmOptions(temperature, maxTokens);
    }

    @Bean
    public LlmClient llmClient(
        RestClient geminiRestClient,
        LlmOptions defaultLlmOptions,
        @Value("${llm.mode:mock}") String mode,
        @Value("${llm.gemini.api-key:}") String apiKey,
        @Value("${llm.gemini.model:}") String model,
        @Value("${llm.mock.always-fail:false}") boolean mockAlwaysFail
    ) {
        if ("gemini".equalsIgnoreCase(mode)) {
            return new GeminiHttpClient(geminiRestClient, apiKey, model, defaultLlmOptions);
        }
        return new MockLlmClient(mockAlwaysFail);
    }

}
