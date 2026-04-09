package com.devlog.devlog.api.controller.sync;

import com.devlog.devlog.api.dto.request.SyncRequest;
import com.devlog.devlog.service.sync.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public ResponseEntity<String> sync(@RequestBody SyncRequest request) {
        String sessionId = syncService.sync(request);
        return ResponseEntity.ok(sessionId);
    }
}
