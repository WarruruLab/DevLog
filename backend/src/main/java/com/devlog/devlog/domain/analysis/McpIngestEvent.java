package com.devlog.devlog.domain.analysis;

import java.time.LocalDateTime;

public class McpIngestEvent {
    private final String eventId;
    private final String sessionId;
    private final String messageId;
    private final String operation;
    private final LocalDateTime processedAt;
    private final String resultStatus;

    public McpIngestEvent(String eventId, String sessionId, String messageId, String operation,
        LocalDateTime processedAt, String resultStatus) {
        this.eventId = eventId;
        this.sessionId = sessionId;
        this.messageId = messageId;
        this.operation = operation;
        this.processedAt = processedAt;
        this.resultStatus = resultStatus;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getOperation() {
        return operation;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getResultStatus() {
        return resultStatus;
    }
}
