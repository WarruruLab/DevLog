package com.devlog.devlog.domain.sync;

import java.time.LocalDateTime;

public class SyncedMessage {
    private final String messageId;
    private final String sessionId;
    private final String content;
    private final String role;
    private final String authorName;
    private final LocalDateTime messageCreatedAt;
    private final String structureStatus;
    private final LocalDateTime structuredAt;

    public SyncedMessage(String messageId, String sessionId, String content, LocalDateTime createdAt) {
        this(messageId, sessionId, content, null, null, createdAt, null, null);
    }

    public SyncedMessage(String messageId, String sessionId, String content, String role,
        String authorName, LocalDateTime messageCreatedAt, String structureStatus,
        LocalDateTime structuredAt) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.content = content;
        this.role = role;
        this.authorName = authorName;
        this.messageCreatedAt = messageCreatedAt;
        this.structureStatus = structureStatus;
        this.structuredAt = structuredAt;
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

    public String getRole() {
        return role;
    }

    public String getAuthorName() {
        return authorName;
    }

    public LocalDateTime getMessageCreatedAt() {
        return messageCreatedAt;
    }

    public String getStructureStatus() {
        return structureStatus;
    }

    public LocalDateTime getStructuredAt() {
        return structuredAt;
    }
}
