package com.devlog.devlog.api.dto.response;

public record AnalysisTriggerResponse(
    String sessionId,
    String sessionStatus,
    String analysisStatus,
    int pendingMessageCount,
    String message
) {}
