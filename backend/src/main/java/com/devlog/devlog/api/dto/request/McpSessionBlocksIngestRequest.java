package com.devlog.devlog.api.dto.request;

import java.util.List;
import java.util.Map;

public record McpSessionBlocksIngestRequest(
    String sessionId,
    String analysisVersion,
    String model,
    String mode,
    List<McpBlockRequest> blocks
) {
    public record McpBlockRequest(
        String mcpBlockId,
        Integer sequenceNo,
        String blockType,
        String title,
        String summary,
        List<String> messageIds,
        Double confidence,
        Map<String, Object> content
    ) {}
}
