package com.devlog.devlog.api.controller.internal;

import com.devlog.devlog.api.dto.request.McpSessionBlocksIngestRequest;
import com.devlog.devlog.api.dto.response.McpSessionBlocksIngestResponse;
import com.devlog.devlog.service.analysis.McpBlockIngestService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mcp")
public class McpIngestController {

    private final McpBlockIngestService ingestService;

    public McpIngestController(McpBlockIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/session-blocks")
    public ResponseEntity<McpSessionBlocksIngestResponse> ingestSessionBlocks(
        @RequestBody McpSessionBlocksIngestRequest request
    ) {
        return ResponseEntity.ok(ingestService.ingest(request));
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
}
