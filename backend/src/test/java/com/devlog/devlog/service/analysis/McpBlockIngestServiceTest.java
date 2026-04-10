package com.devlog.devlog.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest.McpBlockRequest;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class McpBlockIngestServiceTest {

    private LogicalSessionRepository sessionRepository;
    private SyncedMessageRepository messageRepository;
    private SessionBlockRepository blockRepository;
    private SessionBlockMessageRepository blockMessageRepository;
    private PlatformTransactionManager transactionManager;
    private McpBlockIngestService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(LogicalSessionRepository.class);
        messageRepository = mock(SyncedMessageRepository.class);
        blockRepository = mock(SessionBlockRepository.class);
        blockMessageRepository = mock(SessionBlockMessageRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        doNothing().when(transactionManager).rollback(any(TransactionStatus.class));

        service = new McpBlockIngestService(
            sessionRepository,
            messageRepository,
            blockRepository,
            blockMessageRepository,
            transactionManager
        );
    }

    @Test
    void ingest_replacesBlocksAndReturnsMapping() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);
        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(java.util.Optional.of(new LogicalSession(
                sessionId,
                "source-1",
                "session title",
                "READY",
                "IDLE",
                "IDLE",
                2,
                2,
                0,
                2,
                0,
                baseTime,
                baseTime,
                null,
                null,
                null
            )));

        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            new SyncedMessage("m1", sessionId, "first", "USER", null, baseTime, "PENDING", null),
            new SyncedMessage("m2", sessionId, "second", "USER", null, baseTime.plusMinutes(1), "PENDING", null)
        ));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(java.util.Optional.of(baseTime.plusMinutes(1)));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            new SessionBlock(10L, sessionId, 1, "PROBLEM", "old", "old", "{}", "ACTIVE", 1, baseTime, baseTime)
        ));
        when(blockRepository.save(any(SessionBlock.class))).thenReturn(11L, 12L);

        McpSessionBlocksIngestRequest request = new McpSessionBlocksIngestRequest(
            sessionId,
            "v1",
            "model-x",
            "REPLACE",
            List.of(
                new McpBlockRequest(
                    "blk-1",
                    1,
                    "problem",
                    "block one",
                    "summary one",
                    List.of("m1"),
                    0.9,
                    Map.of("source", "mcp")
                ),
                new McpBlockRequest(
                    "blk-2",
                    2,
                    "insight",
                    "block two",
                    "summary two",
                    List.of("m2"),
                    0.8,
                    Map.of("source", "mcp")
                )
            )
        );

        McpSessionBlocksIngestResponse response = service.ingest(request);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.savedBlockCount()).isEqualTo(2);
        assertThat(response.structuredMessageCount()).isEqualTo(2);
        assertThat(response.unstructuredMessageCount()).isEqualTo(0);
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.blockIdMap()).containsEntry("blk-1", 11L).containsEntry("blk-2", 12L);

        verify(blockMessageRepository).deleteBySessionId(sessionId);
        verify(blockRepository).deleteBySessionId(sessionId);
        verify(messageRepository).markPendingBySessionId(sessionId);
        verify(messageRepository, times(1)).markStructured(anyList(), any(LocalDateTime.class));
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "DONE");
        verify(sessionRepository, times(2)).updateAnalysisErrorMessage(sessionId, null);
    }

    @Test
    void ingest_rejectsUnknownMessageId() {
        String sessionId = "session-1";
        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(java.util.Optional.of(new LogicalSession(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            new SyncedMessage("m1", sessionId, "first", LocalDateTime.now())
        ));

        McpSessionBlocksIngestRequest request = new McpSessionBlocksIngestRequest(
            sessionId,
            "v1",
            "model-x",
            "REPLACE",
            List.of(
                new McpBlockRequest(
                    "blk-1",
                    1,
                    "problem",
                    "block one",
                    "summary one",
                    List.of("missing-message"),
                    0.9,
                    Map.of()
                )
            )
        );

        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown messageId");

        verify(blockRepository, never()).save(any(SessionBlock.class));
        verify(sessionRepository, never()).updateSessionStatus(eq(sessionId), eq("READY"));
    }

    @Test
    void ingest_rejectsUnsupportedMode() {
        McpSessionBlocksIngestRequest request = new McpSessionBlocksIngestRequest(
            "session-1",
            "v1",
            "model-x",
            "APPEND",
            List.of(
                new McpBlockRequest(
                    "blk-1",
                    1,
                    "problem",
                    "block one",
                    "summary one",
                    List.of("m1"),
                    0.9,
                    Map.of()
                )
            )
        );

        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("REPLACE mode");
    }
}
