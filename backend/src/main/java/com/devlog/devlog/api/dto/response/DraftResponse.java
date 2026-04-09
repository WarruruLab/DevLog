package com.devlog.devlog.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record DraftResponse(
    Long draftId,
    String sessionId,
    Integer versionNo,
    String status,
    String title,
    String contentMarkdown,
    List<Long> selectedBlockIds,
    LocalDateTime createdAt
) {}
