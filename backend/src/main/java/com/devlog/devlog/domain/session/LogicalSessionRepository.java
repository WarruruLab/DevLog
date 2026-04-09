package com.devlog.devlog.domain.session;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LogicalSessionRepository {
    String save(LogicalSession session);

    String saveOrUpdate(LogicalSession session);

    Optional<LogicalSession> findBySessionId(String sessionId);

    Optional<LogicalSession> findBySourceSessionId(String sourceSessionId);

    List<LogicalSession> findAll();

    void updateSessionStatus(String sessionId, String sessionStatus);

    void updateSyncStatus(String sessionId, String syncStatus);

    void updateAnalysisStatus(String sessionId, String analysisStatus);

    void updateCounts(String sessionId, int totalMessageCount, int syncedMessageCount,
        int structuredMessageCount, int unstructuredMessageCount, int blockCount);

    void updateTimestamps(String sessionId, LocalDateTime lastMessageAt,
        LocalDateTime lastSyncedAt, LocalDateTime lastAnalyzedAt);

    void updateSyncErrorMessage(String sessionId, String syncErrorMessage);

    void updateAnalysisErrorMessage(String sessionId, String analysisErrorMessage);

    void deleteById(String sessionId);
}
