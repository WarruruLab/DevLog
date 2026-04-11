package com.devlog.devlog.api.dto.request;

import java.util.Map;

public record McpSessionBlockEventIngestRequest(
    String sessionId,
    String eventId,
    String messageId,
    String analysisVersion,
    String model,
    String operation,
    TargetBlock targetBlock,
    Map<String, Object> content
) {
    public record TargetBlock(
        String mcpBlockId,
        String blockType,
        String title,
        String summary,
        String status
    ) {}
}
