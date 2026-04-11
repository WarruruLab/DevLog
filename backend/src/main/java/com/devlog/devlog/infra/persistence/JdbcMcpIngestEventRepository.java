package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.analysis.McpIngestEvent;
import com.devlog.devlog.domain.analysis.McpIngestEventRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMcpIngestEventRepository implements McpIngestEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMcpIngestEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveIngestEvent(McpIngestEvent ingestEvent) {
        String sql = """
            INSERT INTO mcp_ingest_event (
                event_id, session_id, message_id, operation, processed_at, result_status
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, ingestEvent.getEventId());
            ps.setString(2, ingestEvent.getSessionId());
            ps.setString(3, ingestEvent.getMessageId());
            ps.setString(4, ingestEvent.getOperation());
            ps.setTimestamp(5, toTimestamp(ingestEvent.getProcessedAt()));
            ps.setString(6, ingestEvent.getResultStatus());
            return ps;
        });
    }

    @Override
    public boolean existsIngestEvent(String eventId) {
        String sql = "SELECT COUNT(*) FROM mcp_ingest_event WHERE event_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, eventId);
        return count != null && count > 0;
    }

    private static Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }
}
