package com.devlog.devlog.domain.analysis;

import java.util.List;

public interface SessionBlockRepository {
    Long save(SessionBlock block);

    List<SessionBlock> findAllBySessionId(String sessionId);

    List<SessionBlock> findByIds(String sessionId, List<Long> blockIds);

    void deleteBySessionId(String sessionId);

    void replaceAll(String sessionId, List<SessionBlock> blocks);
}
