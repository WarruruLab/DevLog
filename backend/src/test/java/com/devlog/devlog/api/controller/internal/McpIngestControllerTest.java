package com.devlog.devlog.api.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest.TargetBlock;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest.McpBlockRequest;
import com.devlog.devlog.api.dto.response.McpSessionActiveBlockResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlockEventIngestResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.service.analysis.McpBlockEventIngestService;
import com.devlog.devlog.service.analysis.McpBlockIngestService;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpIngestControllerTest {

    @Test
    void ingestSessionBlocks_returnsResponse() {
        McpBlockIngestService ingestService = mock(McpBlockIngestService.class);
        McpBlockEventIngestService eventIngestService = mock(McpBlockEventIngestService.class);
        McpIngestController controller = new McpIngestController(ingestService, eventIngestService);

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

    @Test
    void ingestSessionBlockEvent_returnsResponse() throws Exception {
        McpBlockIngestService ingestService = mock(McpBlockIngestService.class);
        McpBlockEventIngestService eventIngestService = mock(McpBlockEventIngestService.class);
        McpIngestController controller = new McpIngestController(ingestService, eventIngestService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(eventIngestService.ingest(org.mockito.ArgumentMatchers.any(McpSessionBlockEventIngestRequest.class)))
            .thenReturn(new McpSessionBlockEventIngestResponse(
                "session-1",
                "evt-1",
                "CREATE_BLOCK",
                "APPLIED",
                101L,
                "blk-1",
                "Created block and mapped message."
            ));

        McpSessionBlockEventIngestRequest request = new McpSessionBlockEventIngestRequest(
            "session-1",
            "evt-1",
            "m1",
            "realtime-v1",
            "model-x",
            "CREATE_BLOCK",
            new TargetBlock("blk-1", "trial", "block one", "summary one", "ACTIVE"),
            Map.of("kind", "create")
        );

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/mcp/session-block-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId":"session-1",
                      "eventId":"evt-1",
                      "messageId":"m1",
                      "analysisVersion":"realtime-v1",
                      "model":"model-x",
                      "operation":"CREATE_BLOCK",
                      "targetBlock":{
                        "mcpBlockId":"blk-1",
                        "blockType":"trial",
                        "title":"block one",
                        "summary":"summary one",
                        "status":"ACTIVE"
                      },
                      "content":{"kind":"create"}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.eventId").value("evt-1"))
            .andExpect(jsonPath("$.operation").value("CREATE_BLOCK"))
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.blockId").value(101))
            .andExpect(jsonPath("$.mcpBlockId").value("blk-1"))
            .andExpect(jsonPath("$.message").value("Created block and mapped message."));
    }

    @Test
    void findActiveBlock_returnsResponse() throws Exception {
        McpBlockIngestService ingestService = mock(McpBlockIngestService.class);
        McpBlockEventIngestService eventIngestService = mock(McpBlockEventIngestService.class);
        McpIngestController controller = new McpIngestController(ingestService, eventIngestService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(eventIngestService.findActiveBlock("session-1"))
            .thenReturn(Optional.of(new McpSessionActiveBlockResponse(
                "session-1",
                101L,
                "blk-1",
                1,
                "TRIAL",
                "block one",
                "summary one",
                "ACTIVE",
                2,
                List.of("m1", "m2"),
                "m2",
                Map.of("rawJson", "{\"kind\":\"current\"}")
            )));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/mcp/sessions/session-1/active-block"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.blockId").value(101))
            .andExpect(jsonPath("$.mcpBlockId").value("blk-1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.messageIds[0]").value("m1"))
            .andExpect(jsonPath("$.messageIds[1]").value("m2"))
            .andExpect(jsonPath("$.lastMessageId").value("m2"))
            .andExpect(jsonPath("$.content.rawJson").value("{\"kind\":\"current\"}"));
    }

    @Test
    void ingestSessionBlockEvent_rejectsBadRequest() throws Exception {
        McpBlockIngestService ingestService = mock(McpBlockIngestService.class);
        McpBlockEventIngestService eventIngestService = mock(McpBlockEventIngestService.class);
        McpIngestController controller = new McpIngestController(ingestService, eventIngestService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/mcp/session-block-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sessionId":" ",
                      "eventId":"evt-1",
                      "messageId":"m1",
                      "analysisVersion":"realtime-v1",
                      "model":"model-x",
                      "operation":"CREATE_BLOCK",
                      "targetBlock":{
                        "mcpBlockId":"blk-1",
                        "blockType":"trial",
                        "title":"block one",
                        "summary":"summary one",
                        "status":"ACTIVE"
                      },
                      "content":{"kind":"create"}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.message", containsString("sessionId")));
    }
}
