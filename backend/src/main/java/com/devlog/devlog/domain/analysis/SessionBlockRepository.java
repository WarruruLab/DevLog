package com.devlog.devlog.domain.analysis;

import java.util.List;
import java.util.Optional;

public interface SessionBlockRepository {
    Long save(SessionBlock block);

    void update(SessionBlock block);

    List<SessionBlock> findAllBySessionId(String sessionId);

    Optional<SessionBlock> findByExternalBlockId(String sessionId, String externalBlockId);

    Optional<SessionBlock> findActiveBlockBySessionId(String sessionId);

    List<SessionBlock> findByIds(String sessionId, List<Long> blockIds);

    void deleteBySessionId(String sessionId);

    void replaceAll(String sessionId, List<SessionBlock> blocks);
}
