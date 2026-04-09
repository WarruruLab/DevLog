package com.devlog.devlog.domain.draft;

import java.time.LocalDateTime;

public class Draft {
    private final Long draftId;
    private final String sessionId;
    private final Integer versionNo;
    private final String status;
    private final String title;
    private final String contentMarkdown;
    private final String generationPrompt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Draft(Long draftId, String sessionId, String content, LocalDateTime createdAt) {
        this(draftId, sessionId, null, null, null, content, null, createdAt, null);
    }

    public Draft(Long draftId, String sessionId, Integer versionNo, String status, String title,
        String contentMarkdown, String generationPrompt, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
        this.draftId = draftId;
        this.sessionId = sessionId;
        this.versionNo = versionNo;
        this.status = status;
        this.title = title;
        this.contentMarkdown = contentMarkdown;
        this.generationPrompt = generationPrompt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getDraftId() {
        return draftId;
    }

    public Long getId() {
        return draftId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public String getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getContentMarkdown() {
        return contentMarkdown;
    }

    public String getContent() {
        return contentMarkdown;
    }

    public String getGenerationPrompt() {
        return generationPrompt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
