package com.devlog.devlog.api.dto.response;

import java.time.LocalDateTime;

public record SessionSummaryResponse(
    String sessionId,
    String sourceSessionId,
    String title,
    String sessionStatus,
    String syncStatus,
    String analysisStatus,
    int totalMessageCount,
    int syncedMessageCount,
    int structuredMessageCount,
    int unstructuredMessageCount,
    int blockCount,
    LocalDateTime lastMessageAt,
    LocalDateTime lastSyncedAt,
    LocalDateTime lastAnalyzedAt
) {}
