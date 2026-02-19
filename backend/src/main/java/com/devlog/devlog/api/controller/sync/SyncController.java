package com.devlog.devlog.api.controller.sync;

import com.devlog.devlog.api.dto.response.InternalMessagePageResponse;
import com.devlog.devlog.service.sync.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService){
        this.syncService = syncService;
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<Void> getSync(@PathVariable String sessionId){
        syncService.sync(sessionId);
        return ResponseEntity.noContent().build();
    }
}
