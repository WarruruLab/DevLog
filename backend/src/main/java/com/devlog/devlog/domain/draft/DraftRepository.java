package com.devlog.devlog.domain.draft;

import java.util.Optional;

public interface DraftRepository {
    // 초안 저장
    void save(Draft draft);
    // 초안 조회
    Optional<Draft> findBySessionId(String sessionId);
}
