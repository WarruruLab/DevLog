package com.devlog.devlog.api.controller.internal;

import com.devlog.devlog.api.dto.request.McpSessionBlockEventIngestRequest;
import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.response.McpSessionActiveBlockResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlockEventIngestResponse;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.service.analysis.McpBlockEventIngestService;
import com.devlog.devlog.service.analysis.McpBlockIngestService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mcp")
public class McpIngestController {

    private final McpBlockIngestService ingestService;
    private final McpBlockEventIngestService eventIngestService;

    public McpIngestController(
        McpBlockIngestService ingestService,
        McpBlockEventIngestService eventIngestService
    ) {
        this.ingestService = ingestService;
        this.eventIngestService = eventIngestService;
    }

    @PostMapping("/session-blocks")
    public ResponseEntity<McpSessionBlocksIngestResponse> ingestSessionBlocks(
        @RequestBody McpSessionBlocksIngestRequest request
    ) {
        return ResponseEntity.ok(ingestService.ingest(request));
    }

    @PostMapping("/session-block-events")
    public ResponseEntity<McpSessionBlockEventIngestResponse> ingestSessionBlockEvent(
        @RequestBody McpSessionBlockEventIngestRequest request
    ) {
        validateSessionBlockEventRequest(request);
        return ResponseEntity.ok(eventIngestService.ingest(request));
    }

    @GetMapping("/sessions/{sessionId}/active-block")
    public ResponseEntity<McpSessionActiveBlockResponse> findActiveBlock(
        @PathVariable String sessionId
    ) {
        requireText(sessionId, "sessionId");
        return eventIngestService.findActiveBlock(sessionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("status", "FAILED", "message", safeMessage(e)));
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }

    private static void validateSessionBlockEventRequest(McpSessionBlockEventIngestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        requireText(request.sessionId(), "sessionId");
        requireText(request.eventId(), "eventId");
        requireText(request.messageId(), "messageId");
        requireText(request.analysisVersion(), "analysisVersion");
        requireText(request.model(), "model");
        requireText(request.operation(), "operation");
        if (request.targetBlock() == null) {
            throw new IllegalArgumentException("targetBlock must not be null");
        }
        requireText(request.targetBlock().mcpBlockId(), "targetBlock.mcpBlockId");
        requireText(request.targetBlock().blockType(), "targetBlock.blockType");
        requireText(request.targetBlock().title(), "targetBlock.title");
        requireText(request.targetBlock().summary(), "targetBlock.summary");
        requireText(request.targetBlock().status(), "targetBlock.status");
        if (request.content() == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
