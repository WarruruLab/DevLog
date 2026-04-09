package com.devlog.devlog.api.dto.response;

import java.util.List;

public record SessionBlockResponse(
    Long blockId,
    String sessionId,
    Integer sequenceNo,
    String blockType,
    String title,
    String summary,
    Integer sourceMessageCount,
    List<String> messageIds
) {}
