package com.devlog.devlog.api.dto.request;

import java.util.List;

public record CreateDraftRequest(
    String sessionId,
    List<Long> selectedBlockIds
) {}
