package com.devlog.devlog.domain.draft;

import java.util.Optional;

public interface DraftRepository {
    Long save(Draft draft);

    Optional<Draft> findBySessionId(String sessionId);

    Optional<Draft> findLatestBySessionId(String sessionId);

    Optional<Draft> findByDraftId(Long draftId);

    int nextVersion(String sessionId);

    void deleteAllBySessionId(String sessionId);
}
