package com.devlog.devlog.api.dto.response;

import java.time.LocalDateTime;

public record InternalMessageResponse(
    String messageId,
    String content,
    String role,
    LocalDateTime createdAt
) {}
