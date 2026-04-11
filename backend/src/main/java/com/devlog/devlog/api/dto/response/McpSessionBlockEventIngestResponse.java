package com.devlog.devlog.api.dto.response;

public record McpSessionBlockEventIngestResponse(
    String sessionId,
    String eventId,
    String operation,
    String status,
    Long blockId,
    String mcpBlockId,
    String message
) {}
