package com.devlog.devlog.domain.draft;

import java.time.LocalDateTime;

public class Draft {
    private final Long id;
    private final String sessionId;
    private final String content;
    private final LocalDateTime createdAt;

    public Draft(Long id, String sessionId, String content, LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
