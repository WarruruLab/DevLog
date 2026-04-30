package com.devlog.devlog.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest.TargetBlock;
import com.devlog.devlog.api.dto.response.McpSessionActiveBlockResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlockEventIngestResponse;
import com.devlog.devlog.domain.analysis.McpIngestEventRepository;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockMessage;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class McpBlockEventIngestServiceTest {

    private LogicalSessionRepository sessionRepository;
    private SyncedMessageRepository messageRepository;
    private SessionBlockRepository blockRepository;
    private SessionBlockMessageRepository blockMessageRepository;
    private McpIngestEventRepository ingestEventRepository;
    private PlatformTransactionManager transactionManager;
    private McpBlockEventIngestService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(LogicalSessionRepository.class);
        messageRepository = mock(SyncedMessageRepository.class);
        blockRepository = mock(SessionBlockRepository.class);
        blockMessageRepository = mock(SessionBlockMessageRepository.class);
        ingestEventRepository = mock(McpIngestEventRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);

        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        doNothing().when(transactionManager).rollback(any(TransactionStatus.class));
        when(blockMessageRepository.findByMessageId(anyString(), anyString())).thenReturn(List.of());

        service = new McpBlockEventIngestService(
            sessionRepository,
            messageRepository,
            blockRepository,
            blockMessageRepository,
            ingestEventRepository,
            transactionManager,
            65536
        );
    }

    @Test
    void ingest_createBlockSuccess() {
        String sessionId = "session-1";
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 10, 10, 0);
        LogicalSession session = session(sessionId);
        SyncedMessage message = message(sessionId, "m1", createdAt, "hello");

        when(ingestEventRepository.existsIngestEvent("evt-1")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(message));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of());
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.empty());
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            createdAt,
            createdAt
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(createdAt));
        when(blockRepository.save(any(SessionBlock.class))).thenReturn(101L);

        McpSessionBlockEventIngestRequest request = createRequest(
            sessionId,
            "evt-1",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "block one",
            "summary one",
            "ACTIVE",
            Map.of("kind", "create")
        );

        McpSessionBlockEventIngestResponse response = service.ingest(request);

        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.blockId()).isEqualTo(101L);
        assertThat(response.mcpBlockId()).isEqualTo("blk-1");
        assertThat(response.message()).isEqualTo("Created block and mapped message.");

        ArgumentCaptor<SessionBlock> blockCaptor = ArgumentCaptor.forClass(SessionBlock.class);
        verify(blockRepository).save(blockCaptor.capture());
        SessionBlock savedBlock = blockCaptor.getValue();
        assertThat(savedBlock.getSessionId()).isEqualTo(sessionId);
        assertThat(savedBlock.getExternalBlockId()).isEqualTo("blk-1");
        assertThat(savedBlock.getBlockType()).isEqualTo("TRIAL");
        assertThat(savedBlock.getTitle()).isEqualTo("block one");
        assertThat(savedBlock.getSummary()).isEqualTo("summary one");
        assertThat(savedBlock.getStatus()).isEqualTo("ACTIVE");

        verify(blockMessageRepository).saveAll(anyList());
        verify(messageRepository).markStructured(eq(List.of("m1")), any(LocalDateTime.class));
        verify(ingestEventRepository).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "DONE");
    }

    @Test
    void ingest_appendSuccess() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);
        LocalDateTime appendTime = baseTime.plusMinutes(1);

        when(ingestEventRepository.existsIngestEvent("evt-2")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first"),
            message(sessionId, "m2", appendTime, "second")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"create\"}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"create\"}",
            "ACTIVE",
            1,
            baseTime,
            appendTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(appendTime));

        McpSessionBlockEventIngestRequest request = createRequest(
            sessionId,
            "evt-2",
            "m2",
            "APPEND",
            "blk-1",
            "trial",
            "block one updated",
            "summary one updated",
            "ACTIVE",
            Map.of("kind", "append")
        );

        McpSessionBlockEventIngestResponse response = service.ingest(request);

        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.blockId()).isEqualTo(101L);
        assertThat(response.message()).isEqualTo("Appended message to target block.");

        ArgumentCaptor<SessionBlock> blockCaptor = ArgumentCaptor.forClass(SessionBlock.class);
        verify(blockRepository).update(blockCaptor.capture());
        SessionBlock updatedBlock = blockCaptor.getValue();
        assertThat(updatedBlock.getSourceMessageCount()).isEqualTo(2);
        assertThat(updatedBlock.getStatus()).isEqualTo("ACTIVE");
        assertThat(updatedBlock.getMessageStartAt()).isEqualTo(baseTime);
        assertThat(updatedBlock.getMessageEndAt()).isEqualTo(appendTime);

        ArgumentCaptor<List<SessionBlockMessage>> mappingCaptor = ArgumentCaptor.forClass(List.class);
        verify(blockMessageRepository).saveAll(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue()).hasSize(1);
        assertThat(mappingCaptor.getValue().get(0).getMessageOrder()).isEqualTo(2);

        verify(messageRepository).markStructured(eq(List.of("m2")), any(LocalDateTime.class));
        verify(ingestEventRepository).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "DONE");
    }

    @Test
    void ingest_finalizeBlockSuccess() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-3")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"create\"}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"create\"}",
            "CLOSED",
            1,
            baseTime,
            baseTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(baseTime));

        McpSessionBlockEventIngestRequest request = createRequest(
            sessionId,
            "evt-3",
            "m1",
            "FINALIZE_BLOCK",
            "blk-1",
            "trial",
            "block one finalized",
            "summary one finalized",
            "CLOSED",
            Map.of("kind", "finalize")
        );

        McpSessionBlockEventIngestResponse response = service.ingest(request);

        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.message()).isEqualTo("Finalized target block.");

        ArgumentCaptor<SessionBlock> blockCaptor = ArgumentCaptor.forClass(SessionBlock.class);
        verify(blockRepository).update(blockCaptor.capture());
        SessionBlock updatedBlock = blockCaptor.getValue();
        assertThat(updatedBlock.getStatus()).isEqualTo("CLOSED");
        assertThat(updatedBlock.getTitle()).isEqualTo("block one finalized");
        assertThat(updatedBlock.getSummary()).isEqualTo("summary one finalized");
        verify(ingestEventRepository).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "DONE");
    }

    @Test
    void ingest_duplicateEventIdIsIgnored() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-4")).thenReturn(false, false, true);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of());
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(
            Optional.empty(),
            Optional.of(block(
                101L,
                sessionId,
                "blk-1",
                1,
                "TRIAL",
                "block one",
                "summary one",
                "{\"kind\":\"create\"}",
                "ACTIVE",
                1,
                baseTime,
                baseTime
            ))
        );
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"create\"}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(baseTime));
        when(blockRepository.save(any(SessionBlock.class))).thenReturn(101L);

        McpSessionBlockEventIngestRequest request = createRequest(
            sessionId,
            "evt-4",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "block one",
            "summary one",
            "ACTIVE",
            Map.of("kind", "create")
        );

        McpSessionBlockEventIngestResponse first = service.ingest(request);
        McpSessionBlockEventIngestResponse second = service.ingest(request);

        assertThat(first.status()).isEqualTo("APPLIED");
        assertThat(second.status()).isEqualTo("IGNORED");
        assertThat(second.message()).isEqualTo("Event already processed.");
        assertThat(second.blockId()).isEqualTo(101L);

        verify(blockRepository, times(1)).save(any(SessionBlock.class));
        verify(blockMessageRepository, times(1)).saveAll(anyList());
        verify(ingestEventRepository, times(1)).saveIngestEvent(any());
    }

    @Test
    void ingest_createBlockWithExistingBlockAndSameMappedMessage_returnsIgnored() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-existing-same")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockMessageRepository.findByMessageId(sessionId, "m1")).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(baseTime));

        McpSessionBlockEventIngestResponse response = service.ingest(createRequest(
            sessionId,
            "evt-existing-same",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "ACTIVE",
            Map.of("incoming", true)
        ));

        assertThat(response.status()).isEqualTo("IGNORED");
        assertThat(response.blockId()).isEqualTo(101L);
        assertThat(response.message()).isEqualTo("Message already mapped to existing block.");

        verify(blockRepository, never()).save(any(SessionBlock.class));
        verify(blockRepository, never()).update(any(SessionBlock.class));
        verify(blockMessageRepository, never()).saveAll(anyList());
        verify(messageRepository, never()).markStructured(anyList(), any(LocalDateTime.class));
        verify(ingestEventRepository).saveIngestEvent(any());
        verify(sessionRepository, never()).updateSessionStatus(sessionId, "FAILED");
    }

    @Test
    void ingest_createBlockWithExistingBlockButMessageNotMapped_recoversMapping() {
        String sessionId = "session-1";
        LocalDateTime firstTime = LocalDateTime.of(2026, 4, 10, 10, 0);
        LocalDateTime secondTime = firstTime.plusMinutes(1);

        when(ingestEventRepository.existsIngestEvent("evt-existing-new-message")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", firstTime, "first"),
            message(sessionId, "m2", secondTime, "second")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, firstTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            firstTime,
            firstTime
        )));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            firstTime,
            secondTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(secondTime));

        McpSessionBlockEventIngestResponse response = service.ingest(createRequest(
            sessionId,
            "evt-existing-new-message",
            "m2",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "ACTIVE",
            Map.of("incoming", true)
        ));

        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.blockId()).isEqualTo(101L);
        assertThat(response.message()).isEqualTo("Mapped message to existing block.");

        verify(blockRepository, never()).save(any(SessionBlock.class));
        verify(blockRepository, never()).update(any(SessionBlock.class));
        ArgumentCaptor<List<SessionBlockMessage>> mappingCaptor = ArgumentCaptor.forClass(List.class);
        verify(blockMessageRepository).saveAll(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue()).hasSize(1);
        assertThat(mappingCaptor.getValue().get(0).getBlockId()).isEqualTo(101L);
        assertThat(mappingCaptor.getValue().get(0).getMessageId()).isEqualTo("m2");
        assertThat(mappingCaptor.getValue().get(0).getMessageOrder()).isEqualTo(2);
        verify(messageRepository).markStructured(eq(List.of("m2")), any(LocalDateTime.class));
        verify(ingestEventRepository).saveIngestEvent(any());
    }

    @Test
    void ingest_createBlockWithExistingBlockButMessageMappedToOtherBlock_failsConflict() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-existing-conflict")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 202L, "m1", 1, baseTime)
        ));
        when(blockMessageRepository.findByMessageId(sessionId, "m1")).thenReturn(List.of(
            mapping(sessionId, 202L, "m1", 1, baseTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));

        assertThatThrownBy(() -> service.ingest(createRequest(
            sessionId,
            "evt-existing-conflict",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "ACTIVE",
            Map.of("incoming", true)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("message already mapped to another block");

        verify(blockRepository, never()).save(any(SessionBlock.class));
        verify(blockRepository, never()).update(any(SessionBlock.class));
        verify(blockMessageRepository, never()).saveAll(anyList());
        verify(ingestEventRepository, never()).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "FAILED");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "FAILED");
    }

    @Test
    void ingest_createBlockDuplicateKeyDuringSave_recoversExistingBlock() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-duplicate-key")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of());
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(
            Optional.empty(),
            Optional.of(block(
                101L,
                sessionId,
                "blk-1",
                1,
                "TRIAL",
                "existing title",
                "existing summary",
                "{\"existing\":true}",
                "ACTIVE",
                0,
                baseTime,
                baseTime
            ))
        );
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "existing title",
            "existing summary",
            "{\"existing\":true}",
            "ACTIVE",
            1,
            baseTime,
            baseTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(baseTime));
        when(blockRepository.save(any(SessionBlock.class))).thenThrow(new DuplicateKeyException("duplicate block"));

        McpSessionBlockEventIngestResponse response = service.ingest(createRequest(
            sessionId,
            "evt-duplicate-key",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "ACTIVE",
            Map.of("incoming", true)
        ));

        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.blockId()).isEqualTo(101L);
        assertThat(response.message()).isEqualTo("Mapped message to existing block.");
        verify(blockMessageRepository).saveAll(anyList());
        verify(messageRepository).markStructured(eq(List.of("m1")), any(LocalDateTime.class));
        verify(ingestEventRepository).saveIngestEvent(any());
    }

    @Test
    void ingest_createBlockWithClosedExistingBlockAndSameMappedMessage_returnsIgnored() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-closed-same")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", baseTime, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockMessageRepository.findByMessageId(sessionId, "m1")).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "closed title",
            "closed summary",
            "{\"closed\":true}",
            "CLOSED",
            1,
            baseTime,
            baseTime
        )));
        when(blockRepository.findAllBySessionId(sessionId)).thenReturn(List.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "closed title",
            "closed summary",
            "{\"closed\":true}",
            "CLOSED",
            1,
            baseTime,
            baseTime
        )));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(1L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.findLastCreatedAtBySessionId(sessionId)).thenReturn(Optional.of(baseTime));

        McpSessionBlockEventIngestResponse response = service.ingest(createRequest(
            sessionId,
            "evt-closed-same",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "CLOSED",
            Map.of("incoming", true)
        ));

        assertThat(response.status()).isEqualTo("IGNORED");
        assertThat(response.blockId()).isEqualTo(101L);
        verify(blockMessageRepository, never()).saveAll(anyList());
        verify(sessionRepository, never()).updateSessionStatus(sessionId, "FAILED");
    }

    @Test
    void ingest_createBlockWithClosedExistingBlockAndDifferentMessage_failsWithoutAppend() {
        String sessionId = "session-1";
        LocalDateTime firstTime = LocalDateTime.of(2026, 4, 10, 10, 0);
        LocalDateTime secondTime = firstTime.plusMinutes(1);

        when(ingestEventRepository.existsIngestEvent("evt-closed-new-message")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", firstTime, "first"),
            message(sessionId, "m2", secondTime, "second")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, firstTime)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-1")).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "closed title",
            "closed summary",
            "{\"closed\":true}",
            "CLOSED",
            1,
            firstTime,
            firstTime
        )));

        assertThatThrownBy(() -> service.ingest(createRequest(
            sessionId,
            "evt-closed-new-message",
            "m2",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "incoming title",
            "incoming summary",
            "CLOSED",
            Map.of("incoming", true)
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target block is not active");

        verify(blockMessageRepository, never()).saveAll(anyList());
        verify(ingestEventRepository, never()).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "FAILED");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "FAILED");
    }

    @Test
    void ingest_appendFailsWhenTargetBlockMissing() {
        String sessionId = "session-1";
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(ingestEventRepository.existsIngestEvent("evt-5")).thenReturn(false, false);
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(messageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            message(sessionId, "m1", createdAt, "first")
        ));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, createdAt)
        ));
        when(blockRepository.findByExternalBlockId(sessionId, "blk-missing")).thenReturn(Optional.empty());

        McpSessionBlockEventIngestRequest request = createRequest(
            sessionId,
            "evt-5",
            "m1",
            "APPEND",
            "blk-missing",
            "trial",
            "block missing",
            "summary missing",
            "ACTIVE",
            Map.of("kind", "append")
        );

        assertThatThrownBy(() -> service.ingest(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target block not found");

        verify(blockRepository, never()).update(any(SessionBlock.class));
        verify(ingestEventRepository, never()).saveIngestEvent(any());
        verify(sessionRepository).updateSessionStatus(sessionId, "FAILED");
        verify(sessionRepository).updateAnalysisStatus(sessionId, "FAILED");
        verify(sessionRepository).updateAnalysisErrorMessage(sessionId, "target block not found: blk-missing");
    }

    @Test
    void ingest_rejectsOversizedContent() {
        service = new McpBlockEventIngestService(
            sessionRepository,
            messageRepository,
            blockRepository,
            blockMessageRepository,
            ingestEventRepository,
            transactionManager,
            10
        );

        assertThatThrownBy(() -> service.ingest(createRequest(
            "session-1",
            "evt-big",
            "m1",
            "CREATE_BLOCK",
            "blk-1",
            "trial",
            "block one",
            "summary one",
            "ACTIVE",
            Map.of("text", "content too large")
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxContentBytes");

        verify(ingestEventRepository, never()).existsIngestEvent(anyString());
    }

    @Test
    void findActiveBlock_returnsSnapshot() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 10, 10, 0);

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(sessionId)));
        when(blockRepository.findActiveBlockBySessionId(sessionId)).thenReturn(Optional.of(block(
            101L,
            sessionId,
            "blk-1",
            1,
            "TRIAL",
            "block one",
            "summary one",
            "{\"kind\":\"current\"}",
            "ACTIVE",
            2,
            baseTime,
            baseTime.plusMinutes(1)
        )));
        when(blockMessageRepository.findAllBySessionId(sessionId)).thenReturn(List.of(
            mapping(sessionId, 101L, "m1", 1, baseTime),
            mapping(sessionId, 101L, "m2", 2, baseTime.plusMinutes(1))
        ));

        Optional<McpSessionActiveBlockResponse> response = service.findActiveBlock(sessionId);

        assertThat(response).isPresent();
        assertThat(response.get().blockId()).isEqualTo(101L);
        assertThat(response.get().mcpBlockId()).isEqualTo("blk-1");
        assertThat(response.get().status()).isEqualTo("ACTIVE");
        assertThat(response.get().sourceMessageCount()).isEqualTo(2);
        assertThat(response.get().messageIds()).containsExactly("m1", "m2");
        assertThat(response.get().lastMessageId()).isEqualTo("m2");
        assertThat(response.get().content()).containsEntry("rawJson", "{\"kind\":\"current\"}");
    }

    private static McpSessionBlockEventIngestRequest createRequest(
        String sessionId,
        String eventId,
        String messageId,
        String operation,
        String mcpBlockId,
        String blockType,
        String title,
        String summary,
        String status,
        Map<String, Object> content
    ) {
        return new McpSessionBlockEventIngestRequest(
            sessionId,
            eventId,
            messageId,
            "realtime-v1",
            "model-x",
            operation,
            new TargetBlock(mcpBlockId, blockType, title, summary, status),
            content
        );
    }

    private static LogicalSession session(String sessionId) {
        return new LogicalSession(
            sessionId,
            "source-1",
            "title",
            "READY",
            "IDLE",
            "IDLE",
            1,
            1,
            0,
            1,
            0,
            LocalDateTime.of(2026, 4, 10, 9, 0),
            LocalDateTime.of(2026, 4, 10, 9, 30),
            null,
            null,
            null
        );
    }

    private static SyncedMessage message(String sessionId, String messageId, LocalDateTime createdAt, String content) {
        return new SyncedMessage(messageId, sessionId, content, "USER", null, createdAt, "PENDING", null);
    }

    private static SessionBlock block(
        Long blockId,
        String sessionId,
        String externalBlockId,
        Integer sequenceNo,
        String blockType,
        String title,
        String summary,
        String contentJson,
        String status,
        Integer sourceMessageCount,
        LocalDateTime messageStartAt,
        LocalDateTime messageEndAt
    ) {
        return new SessionBlock(
            blockId,
            sessionId,
            externalBlockId,
            sequenceNo,
            blockType,
            title,
            summary,
            contentJson,
            status,
            sourceMessageCount,
            messageStartAt,
            messageEndAt
        );
    }

    private static SessionBlockMessage mapping(
        String sessionId,
        Long blockId,
        String messageId,
        int messageOrder,
        LocalDateTime createdAt
    ) {
        return new SessionBlockMessage(null, sessionId, blockId, messageId, messageOrder, createdAt);
    }
}
