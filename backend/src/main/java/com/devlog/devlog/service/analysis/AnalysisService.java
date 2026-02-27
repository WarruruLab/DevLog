package com.devlog.devlog.service.analysis;

import com.devlog.devlog.domain.session.LogicalSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

    private final LogicalSessionRepository sessionRepository;

    // 명시적 생성자 주입
    public AnalysisService(LogicalSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 분석 프로세스를 트리거합니다.
     */
    public void startAnalysis(String sessionId) {
        // 1. 세션 존재 여부 확인
        sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없습니다: " + sessionId));

        // 2. 향후 파이썬 MCP 서버에 분석 명령 전달 로직이 들어갈 자리입니다.
        // TODO: MCP 서버와 통신하여 분석 프로세스 시작 (HTTP/SSE 연동)
    }
}
