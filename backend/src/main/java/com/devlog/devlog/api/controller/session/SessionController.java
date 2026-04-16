package com.devlog.devlog.api.controller.session;

import com.devlog.devlog.api.dto.response.SessionBlockResponse;
import com.devlog.devlog.api.dto.response.SessionDetailResponse;
import com.devlog.devlog.api.dto.response.SessionSummaryResponse;
import com.devlog.devlog.service.session.SessionService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devlog/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionSummaryResponse>> findSessions() {
        return ResponseEntity.ok(sessionService.getSessions());
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> findSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSessionDetail(sessionId));
    }

    @GetMapping("/{sessionId}/blocks")
    public ResponseEntity<List<SessionBlockResponse>> findBlocks(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSessionBlocks(sessionId));
    }
}
