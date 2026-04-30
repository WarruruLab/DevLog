package com.devlog.devlog.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(
        @Value("${devtalk.client.base-url}") String baseUrl,
        @Value("${devtalk.client.connect-timeout-ms:3000}") long connectTimeoutMs,
        @Value("${devtalk.client.read-timeout-ms:10000}") long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }
}
