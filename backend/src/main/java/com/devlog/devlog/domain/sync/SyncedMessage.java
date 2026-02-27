package com.devlog.devlog.domain.sync;

import java.time.LocalDateTime;

public class SyncedMessage {
    private final String messageId;
    private final String sessionId;
    private final String content;
    private final LocalDateTime createdAt;

    public SyncedMessage(String messageId, String sessionId, String content, LocalDateTime createdAt){
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getMessageId() {
        return messageId;
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
