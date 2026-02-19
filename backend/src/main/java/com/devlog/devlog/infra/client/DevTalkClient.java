package com.devlog.devlog.infra.client;

import com.devlog.devlog.api.dto.response.InternalMessagePageResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DevTalkClient {
    private final RestClient restClient;

    public DevTalkClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public InternalMessagePageResponse fetchMessages(String sessionId, String cursor) {
        return restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/internal/messages/{sessionId}")
                .queryParam("cursor", cursor)
                .build(sessionId))
            .retrieve()
            .body(InternalMessagePageResponse.class);
    }
}
