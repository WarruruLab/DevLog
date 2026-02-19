package com.devlog.devlog.domain.analysis;

import java.util.List;

public interface SessionBlockRepository {
    // block 저장
    void save(SessionBlock block);
    // 세션에 해당하는 block 조회
    List<SessionBlock> findAllBySessionId(String sessionId);
}
