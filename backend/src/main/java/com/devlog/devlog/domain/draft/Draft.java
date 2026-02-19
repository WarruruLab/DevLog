package com.devlog.devlog.domain.draft;

public class Draft {
    private final Long id;
    private final String sessionId;
    private final String content;

    public Draft(Long id, String sessionId, String content) {
        this.id = id;
        this.sessionId = sessionId;
        this.content = content;
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
}
