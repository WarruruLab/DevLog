package com.devlog.devlog.domain.session;

import java.util.List;
import java.util.Optional;

public interface LogicalSessionRepository {
    // 세션 저장
    String save(LogicalSession session);
    // 세션 조회
    Optional<LogicalSession> findBySessionId(String sessionId);
    // 모든 세션 조회
    List<LogicalSession> findAll();
    // 세션 삭제
    void deleteById(String sessionId);
}
