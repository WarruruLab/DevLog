package com.devlog.devlog.domain.session;

public class LogicalSession {
    private final String sessionId;

    public LogicalSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
}
