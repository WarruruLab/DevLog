package com.devlog.devlog.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.response.AnalysisTriggerResponse;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    private LogicalSessionRepository sessionRepository;
    private SyncedMessageRepository messageRepository;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(LogicalSessionRepository.class);
        messageRepository = mock(SyncedMessageRepository.class);
        service = new AnalysisService(sessionRepository, messageRepository);
    }

    @Test
    void startAnalysis_marksSessionRunningWithoutCreatingBlocks() {
        String sessionId = "session-1";
        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.of(new LogicalSession(sessionId, sessionId, "My Session",
                "READY", "DONE", "IDLE", 3, 3, 1, 2, 1,
                null, null, null, null, null)));
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(2L);

        AnalysisTriggerResponse response = service.startAnalysis(sessionId);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.analysisStatus()).isEqualTo("RUNNING");
        assertThat(response.pendingMessageCount()).isEqualTo(2);
        verify(sessionRepository).updateSessionStatus(sessionId, "ANALYZING");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "RUNNING");
    }

    @Test
    void startAnalysis_finishesImmediatelyWhenNothingIsPending() {
        String sessionId = "session-1";
        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.of(new LogicalSession(sessionId)));
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);

        AnalysisTriggerResponse response = service.startAnalysis(sessionId);

        assertThat(response.analysisStatus()).isEqualTo("DONE");
        assertThat(response.pendingMessageCount()).isZero();
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "DONE");
        verify(sessionRepository, never()).updateSessionStatus(sessionId, "ANALYZING");
    }

    @Test
    void startAnalysis_failsForUnknownSession() {
        when(sessionRepository.findBySessionId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startAnalysis("missing"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("session not found");
    }
}
