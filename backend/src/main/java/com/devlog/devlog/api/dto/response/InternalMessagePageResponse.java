package com.devlog.devlog.api.dto.response;

import java.util.List;

public record InternalMessagePageResponse(
    List<InternalMessageResponse> messages,
    String nextCursor,
    boolean hasMore
) {}
