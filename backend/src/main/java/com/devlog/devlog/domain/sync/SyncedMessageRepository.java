package com.devlog.devlog.domain.sync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SyncedMessageRepository {
    // 여러개의 메세지 저장
    void saveAll(List<SyncedMessage> syncedMessages);
    // 가장 최근의 날짜를 가져와 cursor를 가져옴
    Optional<LocalDateTime> findLastCreatedAtBySessionId(String sessionId);
    // 세션의 모든 메세지 조회
    List<SyncedMessage> findAllBySessionId(String sessionId);
    // 세션 기준 삭제
    void deleteAllBySessionId(String sessionId);
}
