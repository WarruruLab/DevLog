package com.devlog.devlog.domain.analysis;

import java.util.List;

public interface SessionBlockMessageRepository {
    void saveAll(List<SessionBlockMessage> mappings);

    List<SessionBlockMessage> findAllBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
