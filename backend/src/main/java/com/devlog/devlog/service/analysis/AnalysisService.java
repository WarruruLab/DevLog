package com.devlog.devlog.service.analysis;

import com.devlog.devlog.api.dto.response.AnalysisTriggerResponse;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {

    private final LogicalSessionRepository sessionRepository;
    private final SyncedMessageRepository messageRepository;

    public AnalysisService(
        LogicalSessionRepository sessionRepository,
        SyncedMessageRepository messageRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public AnalysisTriggerResponse startAnalysis(String sessionId) {
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("session not found: " + sessionId));

        int pendingMessageCount = Math.toIntExact(messageRepository.countUnstructuredBySessionId(sessionId));
        sessionRepository.updateAnalysisErrorMessage(sessionId, null);

        if (pendingMessageCount == 0) {
            sessionRepository.updateSessionStatus(sessionId, "READY");
            sessionRepository.updateAnalysisStatus(sessionId, "DONE");
            return new AnalysisTriggerResponse(
                sessionId,
                "READY",
                "DONE",
                0,
                "No pending messages. Waiting for MCP ingest is not required."
            );
        }

        sessionRepository.updateSessionStatus(sessionId, "ANALYZING");
        sessionRepository.updateAnalysisStatus(sessionId, "RUNNING");
        return new AnalysisTriggerResponse(
            sessionId,
            "ANALYZING",
            "RUNNING",
            pendingMessageCount,
            buildQueuedMessage(session, pendingMessageCount)
        );
    }

    private static String buildQueuedMessage(LogicalSession session, int pendingMessageCount) {
        String title = session.getTitle() != null && !session.getTitle().isBlank()
            ? session.getTitle()
            : session.getSessionId();
        return "Analysis queued for MCP ingest for session '" + title + "' with "
            + pendingMessageCount + " pending messages.";
    }
}
