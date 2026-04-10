package com.devlog.devlog.infra.client.dto;

import java.time.LocalDateTime;

public record DevTalkSessionSummaryResponse(
    String sessionId,
    String title,
    String status,
    String description,
    String aiSummary,
    LocalDateTime createdAt,
    LocalDateTime lastUpdatedAt
) {}
