package com.devlog.devlog.service.sync;

import com.devlog.devlog.api.dto.request.SyncRequest;
import com.devlog.devlog.api.dto.response.InternalMessagePageResponse;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import com.devlog.devlog.infra.client.DevTalkClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {

    private final DevTalkClient devTalkClient;
    private final SyncedMessageRepository messageRepository;
    private final LogicalSessionRepository sessionRepository;
    private final int maxPages;

    public SyncService(DevTalkClient devTalkClient, SyncedMessageRepository messageRepository,
        LogicalSessionRepository sessionRepository,
        @Value("${sync.max-pages:1000}") int maxPages) {
        this.devTalkClient = devTalkClient;
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.maxPages = maxPages;
    }

    @Transactional
    public String sync(SyncRequest request) {
        String sessionId = request.sessionId();
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseGet(() -> new LogicalSession(sessionId));
        sessionRepository.saveOrUpdate(session);
        sessionRepository.updateSyncStatus(sessionId, "RUNNING");
        sessionRepository.updateSyncErrorMessage(sessionId, null);
        sessionRepository.updateSessionStatus(sessionId, "SYNCING");

        try {
            String initialCursor = messageRepository.findLastCreatedAtBySessionId(sessionId)
                .map(LocalDateTime::toString)
                .orElse(null);
            syncAllPages(sessionId, initialCursor);

            long totalCount = messageRepository.countBySessionId(sessionId);
            long structuredCount = messageRepository.countStructuredBySessionId(sessionId);
            long unstructuredCount = messageRepository.countUnstructuredBySessionId(sessionId);
            Optional<LocalDateTime> lastMessageAt = messageRepository.findLastCreatedAtBySessionId(sessionId);
            sessionRepository.updateCounts(
                sessionId,
                (int) totalCount,
                (int) totalCount,
                (int) structuredCount,
                (int) unstructuredCount,
                session.getBlockCount()
            );
            sessionRepository.updateTimestamps(
                sessionId,
                lastMessageAt.orElse(session.getLastMessageAt()),
                LocalDateTime.now(),
                session.getLastAnalyzedAt()
            );
            sessionRepository.updateSyncStatus(sessionId, "DONE");
            sessionRepository.updateSessionStatus(sessionId, "READY");
            return sessionId;
        } catch (RuntimeException e) {
            sessionRepository.updateSyncStatus(sessionId, "FAILED");
            sessionRepository.updateSyncErrorMessage(sessionId, e.getMessage());
            sessionRepository.updateSessionStatus(sessionId, "FAILED");
            throw e;
        }
    }

    private void syncAllPages(String sessionId, String initialCursor) {
        String cursor = initialCursor;
        int fetchedPages = 0;

        while (true) {
            if (fetchedPages >= maxPages) {
                throw new IllegalStateException("DevTalk pagination exceeded maxPages: " + maxPages);
            }
            InternalMessagePageResponse response = devTalkClient.fetchMessages(sessionId, cursor);
            fetchedPages++;
            if (response == null) {
                return;
            }

            saveMessages(sessionId, response);

            if (!response.hasMore()) {
                return;
            }

            String nextCursor = response.nextCursor();
            if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
                throw new IllegalStateException("DevTalk pagination returned invalid nextCursor");
            }
            cursor = nextCursor;
        }
    }

    private void saveMessages(String sessionId, InternalMessagePageResponse response) {
        if (response.messages() == null || response.messages().isEmpty()) {
            return;
        }

        List<SyncedMessage> newMessages = response.messages().stream()
            .map(dto -> new SyncedMessage(
                dto.messageId(),
                sessionId,
                dto.content(),
                dto.role(),
                null,
                dto.createdAt(),
                "PENDING",
                null
            ))
            .toList();
        messageRepository.saveAll(newMessages);
    }
}
