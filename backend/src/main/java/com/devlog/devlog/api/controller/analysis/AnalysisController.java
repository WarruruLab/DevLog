package com.devlog.devlog.api.controller.analysis;

import com.devlog.devlog.api.dto.response.SessionBlockResponse;
import com.devlog.devlog.service.analysis.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<SessionBlockResponse> startAnalysis(@PathVariable String sessionId) {
        SessionBlockResponse response = analysisService.startAnalysis(sessionId);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }
}
