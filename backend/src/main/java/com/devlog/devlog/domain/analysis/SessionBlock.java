package com.devlog.devlog.domain.analysis;

public class SessionBlock {
    private final Long blockId;
    private final String sessionId;
    private final String title;
    private final String contentJson;

    public SessionBlock(Long blockId, String sessionId, String title, String contentJson) {
        this.blockId = blockId;
        this.sessionId = sessionId;
        this.title = title;
        this.contentJson = contentJson;
    }

    public Long getBlockId() {
        return blockId;
    }
    public String getSessionId() {
        return sessionId;
    }
    public String getTitle() {
        return title;
    }
    public String getContentJson() {
        return contentJson;
    }
}
