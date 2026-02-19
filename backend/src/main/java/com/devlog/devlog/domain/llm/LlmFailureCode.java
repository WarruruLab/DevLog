package com.devlog.devlog.domain.llm;

public enum LlmFailureCode {
    NETWORK_ERROR,   // 네트워크 에러
    AUTH_ERROR,      // 권한 에러
    RATE_LIMIT,      // 사용량 초과 API 사용시 RPM
    INVALID_REQUEST, // 잘못된 요청, 비어있는 프롬프트
    PROVIDER_ERROR,  // Google 오류 등등
    TIMEOUT,         // 응답시간 초과
    UNKNOWN          // 위의 상황을 제외한 경우
}
