package com.devlog.devlog.domain.llm;

public sealed interface LlmResult permits LlmResult.Success, LlmResult.Failure {
    // 성공 및 실패 중첩 클래스로 인터페이스 내부에 선언되어 static, record는 final
    record Success(String text, LlmFinishReason finishReason, LlmTokenUsage tokenUsage) implements LlmResult {
        public Success {
            if (finishReason == null) finishReason = LlmFinishReason.UNKNOWN;
            if (tokenUsage == null) LlmTokenUsage.empty();
        }

        public static Success of(String text, LlmFinishReason finishReason, LlmTokenUsage tokenUsage) {
            return new Success(text, finishReason, tokenUsage);
        }
    }

    record Failure(LlmFailureCode code, String message, String detail) implements LlmResult {
        // 표준 실패 detail x
        public static Failure of(LlmFailureCode code, String message) {
            return new Failure(code, message, null);
        }
        // detail까지 포함한 디버깅용 실패
        public static Failure of(LlmFailureCode code, String message, String detail) {
            return new Failure(code, message, detail);
        }
    }
}
