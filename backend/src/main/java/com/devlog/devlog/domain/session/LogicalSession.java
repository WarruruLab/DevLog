package com.devlog.devlog.domain.session;

import java.time.LocalDateTime;

public class LogicalSession {
    private final String sessionId;
    private final String sourceSessionId;
    private final String title;
    private final String sessionStatus;
    private final String syncStatus;
    private final String analysisStatus;
    private final int totalMessageCount;
    private final int syncedMessageCount;
    private final int structuredMessageCount;
    private final int unstructuredMessageCount;
    private final int blockCount;
    private final LocalDateTime lastMessageAt;
    private final LocalDateTime lastSyncedAt;
    private final LocalDateTime lastAnalyzedAt;
    private final String syncErrorMessage;
    private final String analysisErrorMessage;

    public LogicalSession(String sessionId) {
        this(sessionId, null, null, null, null, null, 0, 0, 0, 0, 0, null, null, null, null, null);
    }

    public LogicalSession(String sessionId, String sourceSessionId, String title,
        String sessionStatus, String syncStatus, String analysisStatus,
        int totalMessageCount, int syncedMessageCount, int structuredMessageCount,
        int unstructuredMessageCount, int blockCount, LocalDateTime lastMessageAt,
        LocalDateTime lastSyncedAt, LocalDateTime lastAnalyzedAt,
        String syncErrorMessage, String analysisErrorMessage) {
        this.sessionId = sessionId;
        this.sourceSessionId = sourceSessionId;
        this.title = title;
        this.sessionStatus = sessionStatus;
        this.syncStatus = syncStatus;
        this.analysisStatus = analysisStatus;
        this.totalMessageCount = totalMessageCount;
        this.syncedMessageCount = syncedMessageCount;
        this.structuredMessageCount = structuredMessageCount;
        this.unstructuredMessageCount = unstructuredMessageCount;
        this.blockCount = blockCount;
        this.lastMessageAt = lastMessageAt;
        this.lastSyncedAt = lastSyncedAt;
        this.lastAnalyzedAt = lastAnalyzedAt;
        this.syncErrorMessage = syncErrorMessage;
        this.analysisErrorMessage = analysisErrorMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSourceSessionId() {
        return sourceSessionId;
    }

    public String getTitle() {
        return title;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public int getTotalMessageCount() {
        return totalMessageCount;
    }

    public int getSyncedMessageCount() {
        return syncedMessageCount;
    }

    public int getStructuredMessageCount() {
        return structuredMessageCount;
    }

    public int getUnstructuredMessageCount() {
        return unstructuredMessageCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public LocalDateTime getLastAnalyzedAt() {
        return lastAnalyzedAt;
    }

    public String getSyncErrorMessage() {
        return syncErrorMessage;
    }

    public String getAnalysisErrorMessage() {
        return analysisErrorMessage;
    }
}
