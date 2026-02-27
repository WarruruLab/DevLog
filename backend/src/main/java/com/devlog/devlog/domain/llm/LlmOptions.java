package com.devlog.devlog.domain.llm;

public record LlmOptions(
    /**
     * temperature
     *
     * 응답의 랜덤성을 조절하는 값
     * - 낮을수록 (0.0 ~ 0.3): 결정적인 응답, 같은 입력 → 거의 같은 출력
     *
     * - 높을수록 (0.7 ~ 1.0): 창의적인 응답, 같은 입력 → 다양한 출력
     *
     * - 기록과 재현성이 중요하므로 낮은 값 사용
     */
    Double temperature,
    /**
     * maxTokens
     *
     * AI 응답의 최대 길이 제한
     *
     * - 너무 긴 응답으로 인한 비용 증가 방지
     * - 응답 지연/타임아웃 방지
     * - 로그로 남기기에 적절한 크기 유지
     *
     */
    Integer maxTokens
) {
    public static LlmOptions defaults() {
        return new LlmOptions(0.2, 65536);
    }
}
