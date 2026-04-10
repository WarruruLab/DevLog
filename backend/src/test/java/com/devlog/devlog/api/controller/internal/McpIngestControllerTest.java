package com.devlog.devlog.api.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest.McpBlockRequest;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.service.analysis.McpBlockIngestService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class McpIngestControllerTest {

    @Test
    void ingestSessionBlocks_returnsResponse() {
        McpBlockIngestService ingestService = mock(McpBlockIngestService.class);
        McpIngestController controller = new McpIngestController(ingestService);

        when(ingestService.ingest(org.mockito.ArgumentMatchers.any(McpSessionBlocksIngestRequest.class)))
            .thenReturn(new McpSessionBlocksIngestResponse(
                "session-1",
                1,
                2,
                0,
                Map.of("blk-1", 10L),
                "DONE"
            ));

        McpSessionBlocksIngestRequest request = new McpSessionBlocksIngestRequest(
            "session-1",
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
                )
            )
        );

        ResponseEntity<McpSessionBlocksIngestResponse> response = controller.ingestSessionBlocks(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sessionId()).isEqualTo("session-1");
        assertThat(response.getBody().savedBlockCount()).isEqualTo(1);
        assertThat(response.getBody().structuredMessageCount()).isEqualTo(2);
        assertThat(response.getBody().blockIdMap()).containsEntry("blk-1", 10L);
    }
}
