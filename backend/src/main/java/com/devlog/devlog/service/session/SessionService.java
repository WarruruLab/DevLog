package com.devlog.devlog.service.session;

import com.devlog.devlog.api.dto.response.SessionBlockResponse;
import com.devlog.devlog.api.dto.response.SessionDetailResponse;
import com.devlog.devlog.api.dto.response.SessionSummaryResponse;
import com.devlog.devlog.infra.client.DevTalkClient;
import com.devlog.devlog.infra.client.dto.DevTalkSessionSummaryResponse;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockMessage;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final LogicalSessionRepository sessionRepository;
    private final SessionBlockRepository blockRepository;
    private final SessionBlockMessageRepository blockMessageRepository;
    private final DevTalkClient devTalkClient;

    public SessionService(LogicalSessionRepository sessionRepository,
        SessionBlockRepository blockRepository,
        SessionBlockMessageRepository blockMessageRepository, DevTalkClient devTalkClient) {
        this.sessionRepository = sessionRepository;
        this.blockRepository = blockRepository;
        this.blockMessageRepository = blockMessageRepository;
        this.devTalkClient = devTalkClient;
    }

    public List<SessionSummaryResponse> getSessions() {
        refreshDevTalkMetadata();
        return sessionRepository.findAll().stream()
            .sorted(Comparator.comparing(SessionService::sortTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSessionDetail(String sessionId) {
        LogicalSession session = requireSession(sessionId);
        return toDetailResponse(session);
    }

    @Transactional(readOnly = true)
    public List<SessionBlockResponse> getSessionBlocks(String sessionId) {
        requireSession(sessionId);

        List<SessionBlock> blocks = blockRepository.findAllBySessionId(sessionId).stream()
            .filter(SessionService::isActive)
            .toList();

        Map<Long, List<SessionBlockMessage>> messagesByBlockId = blockMessageRepository
            .findAllBySessionId(sessionId)
            .stream()
            .collect(Collectors.groupingBy(
                SessionBlockMessage::getBlockId,
                Collectors.collectingAndThen(Collectors.toList(), messages -> messages.stream()
                    .sorted(Comparator.comparingInt(SessionService::messageOrder))
                    .toList())
            ));

        return blocks.stream()
            .map(block -> new SessionBlockResponse(
                block.getBlockId(),
                block.getSessionId(),
                block.getSequenceNo(),
                block.getBlockType(),
                block.getTitle(),
                block.getSummary(),
                block.getSourceMessageCount(),
                messageIdsForBlock(messagesByBlockId.get(block.getBlockId()))
            ))
            .toList();
    }

    private SessionSummaryResponse toSummaryResponse(LogicalSession session) {
        return new SessionSummaryResponse(
            session.getSessionId(),
            session.getSourceSessionId(),
            session.getTitle(),
            session.getSessionStatus(),
            session.getSyncStatus(),
            session.getAnalysisStatus(),
            session.getTotalMessageCount(),
            session.getSyncedMessageCount(),
            session.getStructuredMessageCount(),
            session.getUnstructuredMessageCount(),
            session.getBlockCount(),
            session.getLastMessageAt(),
            session.getLastSyncedAt(),
            session.getLastAnalyzedAt()
        );
    }

    private void refreshDevTalkMetadata() {
        try {
            List<DevTalkSessionSummaryResponse> devTalkSessions = devTalkClient.fetchSessions();
            if (devTalkSessions == null || devTalkSessions.isEmpty()) {
                return;
            }

            for (DevTalkSessionSummaryResponse devTalkSession : devTalkSessions) {
                if (devTalkSession == null || devTalkSession.sessionId() == null
                    || devTalkSession.sessionId().isBlank()) {
                    continue;
                }

                sessionRepository.upsertMetadata(
                    devTalkSession.sessionId(),
                    devTalkSession.sessionId(),
                    devTalkSession.title()
                );
            }
        } catch (RuntimeException e) {
            log.warn("Failed to refresh DevTalk session metadata, falling back to local DB list: {}",
                e.getMessage());
        }
    }

    private SessionDetailResponse toDetailResponse(LogicalSession session) {
        return new SessionDetailResponse(
            session.getSessionId(),
            session.getSourceSessionId(),
            session.getTitle(),
            session.getSessionStatus(),
            session.getSyncStatus(),
            session.getAnalysisStatus(),
            session.getTotalMessageCount(),
            session.getSyncedMessageCount(),
            session.getStructuredMessageCount(),
            session.getUnstructuredMessageCount(),
            session.getBlockCount(),
            session.getLastMessageAt(),
            session.getLastSyncedAt(),
            session.getLastAnalyzedAt(),
            session.getSyncErrorMessage(),
            session.getAnalysisErrorMessage()
        );
    }

    private LogicalSession requireSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Session not found: " + sessionId
            ));
    }

    private List<String> messageIdsForBlock(List<SessionBlockMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        return messages.stream()
            .sorted(Comparator.comparingInt(SessionService::messageOrder))
            .map(SessionBlockMessage::getMessageId)
            .filter(Objects::nonNull)
            .toList();
    }

    private static boolean isActive(SessionBlock block) {
        return block.getStatus() == null || "ACTIVE".equalsIgnoreCase(block.getStatus());
    }

    private static int messageOrder(SessionBlockMessage message) {
        return message.getMessageOrder() != null ? message.getMessageOrder() : Integer.MAX_VALUE;
    }

    private static LocalDateTime sortTime(LogicalSession session) {
        if (session.getLastSyncedAt() != null) {
            return session.getLastSyncedAt();
        }
        if (session.getLastMessageAt() != null) {
            return session.getLastMessageAt();
        }
        return session.getLastAnalyzedAt();
    }
}
