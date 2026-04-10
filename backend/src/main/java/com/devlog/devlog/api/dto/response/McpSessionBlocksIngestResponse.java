package com.devlog.devlog.api.dto.response;

import java.util.Map;

public record McpSessionBlocksIngestResponse(
    String sessionId,
    int savedBlockCount,
    int structuredMessageCount,
    int unstructuredMessageCount,
    Map<String, Long> blockIdMap,
    String status
) {}
