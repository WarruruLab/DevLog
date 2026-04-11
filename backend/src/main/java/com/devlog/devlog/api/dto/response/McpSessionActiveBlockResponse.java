package com.devlog.devlog.api.dto.response;

import java.util.List;
import java.util.Map;

public record McpSessionActiveBlockResponse(
    String sessionId,
    Long blockId,
    String mcpBlockId,
    Integer sequenceNo,
    String blockType,
    String title,
    String summary,
    String status,
    Integer sourceMessageCount,
    List<String> messageIds,
    String lastMessageId,
    Map<String, Object> content
) {}
