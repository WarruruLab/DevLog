package com.devlog.devlog.domain.analysis;

import java.time.LocalDateTime;

public class SessionBlock {
    private final Long blockId;
    private final String sessionId;
    private final String externalBlockId;
    private final Integer sequenceNo;
    private final String blockType;
    private final String title;
    private final String summary;
    private final String contentJson;
    private final String status;
    private final Integer sourceMessageCount;
    private final LocalDateTime messageStartAt;
    private final LocalDateTime messageEndAt;

    public SessionBlock(Long blockId, String sessionId, String title, String contentJson) {
        this(blockId, sessionId, null, null, null, title, null, contentJson, null, null, null, null);
    }

    public SessionBlock(Long blockId, String sessionId, Integer sequenceNo, String blockType,
        String title, String summary, String contentJson, String status,
        Integer sourceMessageCount, LocalDateTime messageStartAt,
        LocalDateTime messageEndAt) {
        this(blockId, sessionId, null, sequenceNo, blockType, title, summary, contentJson,
            status, sourceMessageCount, messageStartAt, messageEndAt);
    }

    public SessionBlock(Long blockId, String sessionId, String externalBlockId, Integer sequenceNo,
        String blockType, String title, String summary, String contentJson, String status,
        Integer sourceMessageCount, LocalDateTime messageStartAt, LocalDateTime messageEndAt) {
        this.blockId = blockId;
        this.sessionId = sessionId;
        this.externalBlockId = externalBlockId;
        this.sequenceNo = sequenceNo;
        this.blockType = blockType;
        this.title = title;
        this.summary = summary;
        this.contentJson = contentJson;
        this.status = status;
        this.sourceMessageCount = sourceMessageCount;
        this.messageStartAt = messageStartAt;
        this.messageEndAt = messageEndAt;
    }

    public Long getBlockId() {
        return blockId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getExternalBlockId() {
        return externalBlockId;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public String getBlockType() {
        return blockType;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getStatus() {
        return status;
    }

    public Integer getSourceMessageCount() {
        return sourceMessageCount;
    }

    public LocalDateTime getMessageStartAt() {
        return messageStartAt;
    }

    public LocalDateTime getMessageEndAt() {
        return messageEndAt;
    }
}
