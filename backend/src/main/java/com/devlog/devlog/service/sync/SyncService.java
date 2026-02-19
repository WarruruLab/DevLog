package com.devlog.devlog.service.sync;

import com.devlog.devlog.api.dto.response.InternalMessagePageResponse;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import com.devlog.devlog.infra.client.DevTalkClient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {

    private final DevTalkClient devTalkClient;
    private final SyncedMessageRepository messageRepository;
    private final LogicalSessionRepository sessionRepository;

    public SyncService(DevTalkClient devTalkClient, SyncedMessageRepository messageRepository, LogicalSessionRepository sessionRepository) {
        this.devTalkClient = devTalkClient;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void sync(String sessionId) {
        // 1. 세션 존재 여부 확인 및 저장
        sessionRepository.save(new LogicalSession(sessionId));

        // 2. 마지막 동기화 시점 확인
        // 만약 처음이라면 null을 반환하여
        String cursor = messageRepository.findLastCreatedAtBySessionId(sessionId)
            .map(LocalDateTime::toString)
            .orElse(null);

        // 3. DevTalk API 호출
        InternalMessagePageResponse response = devTalkClient.fetchMessages(sessionId, cursor);

        // 4. 받아온 DTO를 도메인 엔티티(SyncedMessage)로 변환 후 Batch Insert
        if (response != null && !response.messages().isEmpty()) {
            List<SyncedMessage> newMessages = response.messages().stream()
                .map(dto -> new SyncedMessage(
                    dto.messageId(),
                    sessionId,
                    dto.content(),
                    dto.createdAt()
                ))
                .toList();

            messageRepository.saveAll(newMessages);
        }
    }
}
