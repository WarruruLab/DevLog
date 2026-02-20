package com.devlog.devlog.api.controller.analysis;

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

    // 세션에 대해서 MCP 실행
    @PostMapping("/{sessionId}")
    public ResponseEntity<Void> startAnalysis(@PathVariable String sessionId) {
        analysisService.startAnalysis(sessionId);
        return ResponseEntity.ok().build();
    }
}
