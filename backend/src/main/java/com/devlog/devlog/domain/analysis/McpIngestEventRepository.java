package com.devlog.devlog.domain.analysis;

public interface McpIngestEventRepository {
    void saveIngestEvent(McpIngestEvent ingestEvent);

    boolean existsIngestEvent(String eventId);
}
