package com.devlog.devlog.api.controller.analysis;

import com.devlog.devlog.api.dto.response.AnalysisTriggerResponse;
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
    public ResponseEntity<AnalysisTriggerResponse> startAnalysis(@PathVariable String sessionId) {
        AnalysisTriggerResponse response = analysisService.startAnalysis(sessionId);
        return "RUNNING".equalsIgnoreCase(response.analysisStatus())
            ? ResponseEntity.accepted().body(response)
            : ResponseEntity.ok(response);
    }
}
