package com.devlog.devlog.domain.llm;

public enum LlmFinishReason {
    STOP,          // 정상 종료
    MAX_TOKENS,    // 토큰 한계로 끊김
    SAFETY,        // 정책 차단
    OTHER,         // 기타(LLM에서 명시)
    UNKNOWN        // 파싱 불가/미지원/비정상
}
