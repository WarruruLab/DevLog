package com.devlog.devlog.domain.sync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SyncedMessageRepository {
    void saveAll(List<SyncedMessage> syncedMessages);

    Optional<LocalDateTime> findLastCreatedAtBySessionId(String sessionId);

    List<SyncedMessage> findAllBySessionId(String sessionId);

    long countBySessionId(String sessionId);

    long countStructuredBySessionId(String sessionId);

    long countUnstructuredBySessionId(String sessionId);

    void markStructured(List<String> messageIds, LocalDateTime structuredAt);

    void markPendingBySessionId(String sessionId);

    void deleteAllBySessionId(String sessionId);
}
