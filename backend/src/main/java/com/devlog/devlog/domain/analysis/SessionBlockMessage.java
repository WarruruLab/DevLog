package com.devlog.devlog.domain.analysis;

import java.time.LocalDateTime;

public class SessionBlockMessage {
    private final Long id;
    private final String sessionId;
    private final Long blockId;
    private final String messageId;
    private final Integer messageOrder;
    private final LocalDateTime createdAt;

    public SessionBlockMessage(Long id, String sessionId, Long blockId, String messageId,
        Integer messageOrder, LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.blockId = blockId;
        this.messageId = messageId;
        this.messageOrder = messageOrder;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getBlockId() {
        return blockId;
    }

    public String getMessageId() {
        return messageId;
    }

    public Integer getMessageOrder() {
        return messageOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
