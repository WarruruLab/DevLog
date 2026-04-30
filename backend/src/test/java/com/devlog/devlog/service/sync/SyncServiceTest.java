package com.devlog.devlog.service.sync;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.request.SyncRequest;
import com.devlog.devlog.api.dto.response.InternalMessagePageResponse;
import com.devlog.devlog.api.dto.response.InternalMessageResponse;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import com.devlog.devlog.infra.client.DevTalkClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyncServiceTest {

    private DevTalkClient devTalkClient;
    private SyncedMessageRepository messageRepository;
    private LogicalSessionRepository sessionRepository;
    private SyncService service;

    @BeforeEach
    void setUp() {
        devTalkClient = mock(DevTalkClient.class);
        messageRepository = mock(SyncedMessageRepository.class);
        sessionRepository = mock(LogicalSessionRepository.class);
        service = new SyncService(devTalkClient, messageRepository, sessionRepository, 1000);
    }

    @Test
    void sync_fetchesAllPagesUntilHasMoreIsFalse() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 11, 10, 0);

        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.of(new LogicalSession(sessionId)));
        when(messageRepository.findLastCreatedAtBySessionId(sessionId))
            .thenReturn(Optional.of(baseTime), Optional.of(baseTime.plusMinutes(2)));
        when(devTalkClient.fetchMessages(sessionId, baseTime.toString()))
            .thenReturn(new InternalMessagePageResponse(
                List.of(new InternalMessageResponse("m1", "first", "USER", baseTime.plusMinutes(1))),
                baseTime.plusMinutes(1).toString(),
                true
            ));
        when(devTalkClient.fetchMessages(sessionId, baseTime.plusMinutes(1).toString()))
            .thenReturn(new InternalMessagePageResponse(
                List.of(new InternalMessageResponse("m2", "second", "ASSISTANT", baseTime.plusMinutes(2))),
                null,
                false
            ));
        when(messageRepository.countBySessionId(sessionId)).thenReturn(2L);
        when(messageRepository.countStructuredBySessionId(sessionId)).thenReturn(0L);
        when(messageRepository.countUnstructuredBySessionId(sessionId)).thenReturn(2L);

        service.sync(new SyncRequest(sessionId));

        verify(devTalkClient).fetchMessages(sessionId, baseTime.toString());
        verify(devTalkClient).fetchMessages(sessionId, baseTime.plusMinutes(1).toString());
        verify(messageRepository, times(2)).saveAll(anyList());
        verify(sessionRepository).updateSyncStatus(sessionId, "DONE");
        verify(sessionRepository).updateSessionStatus(sessionId, "READY");
    }

    @Test
    void sync_failsWhenHasMoreWithoutValidNextCursor() {
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 11, 10, 0);

        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.of(new LogicalSession(sessionId)));
        when(messageRepository.findLastCreatedAtBySessionId(sessionId))
            .thenReturn(Optional.of(baseTime));
        when(devTalkClient.fetchMessages(sessionId, baseTime.toString()))
            .thenReturn(new InternalMessagePageResponse(
                List.of(new InternalMessageResponse("m1", "first", "USER", baseTime.plusMinutes(1))),
                " ",
                true
            ));

        assertThatThrownBy(() -> service.sync(new SyncRequest(sessionId)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid nextCursor");

        verify(messageRepository).saveAll(anyList());
        verify(sessionRepository).updateSyncStatus(sessionId, "FAILED");
        verify(sessionRepository).updateSessionStatus(sessionId, "FAILED");
        verify(sessionRepository, never()).updateSyncStatus(eq(sessionId), eq("DONE"));
    }

    @Test
    void sync_failsWhenPageLimitExceeded() {
        service = new SyncService(devTalkClient, messageRepository, sessionRepository, 1);
        String sessionId = "session-1";
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 11, 10, 0);

        when(sessionRepository.findBySessionId(sessionId))
            .thenReturn(Optional.of(new LogicalSession(sessionId)));
        when(messageRepository.findLastCreatedAtBySessionId(sessionId))
            .thenReturn(Optional.of(baseTime));
        when(devTalkClient.fetchMessages(sessionId, baseTime.toString()))
            .thenReturn(new InternalMessagePageResponse(
                List.of(new InternalMessageResponse("m1", "first", "USER", baseTime.plusMinutes(1))),
                baseTime.plusMinutes(1).toString(),
                true
            ));

        assertThatThrownBy(() -> service.sync(new SyncRequest(sessionId)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("maxPages");

        verify(devTalkClient, times(1)).fetchMessages(sessionId, baseTime.toString());
        verify(sessionRepository).updateSyncStatus(sessionId, "FAILED");
    }
}
