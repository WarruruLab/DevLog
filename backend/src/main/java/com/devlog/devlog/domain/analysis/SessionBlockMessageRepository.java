package com.devlog.devlog.domain.analysis;

import java.util.List;

public interface SessionBlockMessageRepository {
    void saveAll(List<SessionBlockMessage> mappings);

    List<SessionBlockMessage> findAllBySessionId(String sessionId);

    List<SessionBlockMessage> findByMessageId(String sessionId, String messageId);

    boolean existsBlockMessage(Long blockId, String messageId);

    void deleteBySessionId(String sessionId);
}
