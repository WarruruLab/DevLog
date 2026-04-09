package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLogicalSessionRepository implements LogicalSessionRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcLogicalSessionRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String save(LogicalSession session) {
        return saveOrUpdate(session);
    }

    @Override
    public String saveOrUpdate(LogicalSession session) {
        String sql = """
            INSERT INTO logical_session (
                session_id, source_session_id, title, session_status, sync_status, analysis_status,
                total_message_count, synced_message_count, structured_message_count,
                unstructured_message_count, block_count, last_message_at, last_synced_at,
                last_analyzed_at, sync_error_message, analysis_error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                source_session_id = VALUES(source_session_id),
                title = VALUES(title),
                session_status = VALUES(session_status),
                sync_status = VALUES(sync_status),
                analysis_status = VALUES(analysis_status),
                total_message_count = VALUES(total_message_count),
                synced_message_count = VALUES(synced_message_count),
                structured_message_count = VALUES(structured_message_count),
                unstructured_message_count = VALUES(unstructured_message_count),
                block_count = VALUES(block_count),
                last_message_at = VALUES(last_message_at),
                last_synced_at = VALUES(last_synced_at),
                last_analyzed_at = VALUES(last_analyzed_at),
                sync_error_message = VALUES(sync_error_message),
                analysis_error_message = VALUES(analysis_error_message)
            """;

        String sessionId = session.getSessionId();
        String sourceSessionId = defaultString(session.getSourceSessionId(), sessionId);
        String title = defaultString(session.getTitle(), sessionId);
        String sessionStatus = defaultString(session.getSessionStatus(), "READY");
        String syncStatus = defaultString(session.getSyncStatus(), "IDLE");
        String analysisStatus = defaultString(session.getAnalysisStatus(), "IDLE");

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, sessionId);
            ps.setString(2, sourceSessionId);
            ps.setString(3, title);
            ps.setString(4, sessionStatus);
            ps.setString(5, syncStatus);
            ps.setString(6, analysisStatus);
            ps.setInt(7, session.getTotalMessageCount());
            ps.setInt(8, session.getSyncedMessageCount());
            ps.setInt(9, session.getStructuredMessageCount());
            ps.setInt(10, session.getUnstructuredMessageCount());
            ps.setInt(11, session.getBlockCount());
            ps.setTimestamp(12, toTimestamp(session.getLastMessageAt()));
            ps.setTimestamp(13, toTimestamp(session.getLastSyncedAt()));
            ps.setTimestamp(14, toTimestamp(session.getLastAnalyzedAt()));
            ps.setString(15, session.getSyncErrorMessage());
            ps.setString(16, session.getAnalysisErrorMessage());
            return ps;
        });

        return sessionId;
    }

    @Override
    public Optional<LogicalSession> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM logical_session WHERE session_id = ?";
        try {
            LogicalSession session = jdbcTemplate.queryForObject(sql, logicalSessionRowMapper, sessionId);
            return Optional.ofNullable(session);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<LogicalSession> findBySourceSessionId(String sourceSessionId) {
        String sql = "SELECT * FROM logical_session WHERE source_session_id = ?";
        try {
            LogicalSession session = jdbcTemplate.queryForObject(sql, logicalSessionRowMapper, sourceSessionId);
            return Optional.ofNullable(session);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<LogicalSession> findAll() {
        String sql = "SELECT * FROM logical_session ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, logicalSessionRowMapper);
    }

    @Override
    public void updateSessionStatus(String sessionId, String sessionStatus) {
        jdbcTemplate.update("UPDATE logical_session SET session_status = ? WHERE session_id = ?",
            sessionStatus, sessionId);
    }

    @Override
    public void updateSyncStatus(String sessionId, String syncStatus) {
        jdbcTemplate.update("UPDATE logical_session SET sync_status = ? WHERE session_id = ?",
            syncStatus, sessionId);
    }

    @Override
    public void updateAnalysisStatus(String sessionId, String analysisStatus) {
        jdbcTemplate.update("UPDATE logical_session SET analysis_status = ? WHERE session_id = ?",
            analysisStatus, sessionId);
    }

    @Override
    public void updateCounts(String sessionId, int totalMessageCount, int syncedMessageCount,
        int structuredMessageCount, int unstructuredMessageCount, int blockCount) {
        jdbcTemplate.update("""
                UPDATE logical_session
                SET total_message_count = ?,
                    synced_message_count = ?,
                    structured_message_count = ?,
                    unstructured_message_count = ?,
                    block_count = ?
                WHERE session_id = ?
            """,
            totalMessageCount, syncedMessageCount, structuredMessageCount,
            unstructuredMessageCount, blockCount, sessionId);
    }

    @Override
    public void updateTimestamps(String sessionId, java.time.LocalDateTime lastMessageAt,
        java.time.LocalDateTime lastSyncedAt, java.time.LocalDateTime lastAnalyzedAt) {
        jdbcTemplate.update("""
                UPDATE logical_session
                SET last_message_at = ?,
                    last_synced_at = ?,
                    last_analyzed_at = ?
                WHERE session_id = ?
            """,
            toTimestamp(lastMessageAt), toTimestamp(lastSyncedAt), toTimestamp(lastAnalyzedAt), sessionId);
    }

    @Override
    public void updateSyncErrorMessage(String sessionId, String syncErrorMessage) {
        jdbcTemplate.update("UPDATE logical_session SET sync_error_message = ? WHERE session_id = ?",
            syncErrorMessage, sessionId);
    }

    @Override
    public void updateAnalysisErrorMessage(String sessionId, String analysisErrorMessage) {
        jdbcTemplate.update("UPDATE logical_session SET analysis_error_message = ? WHERE session_id = ?",
            analysisErrorMessage, sessionId);
    }

    @Override
    public void deleteById(String sessionId) {
        jdbcTemplate.update("DELETE FROM logical_session WHERE session_id = ?", sessionId);
    }

    private final RowMapper<LogicalSession> logicalSessionRowMapper = (rs, rowNum) -> new LogicalSession(
        rs.getString("session_id"),
        rs.getString("source_session_id"),
        rs.getString("title"),
        rs.getString("session_status"),
        rs.getString("sync_status"),
        rs.getString("analysis_status"),
        rs.getInt("total_message_count"),
        rs.getInt("synced_message_count"),
        rs.getInt("structured_message_count"),
        rs.getInt("unstructured_message_count"),
        rs.getInt("block_count"),
        toLocalDateTime(rs.getTimestamp("last_message_at")),
        toLocalDateTime(rs.getTimestamp("last_synced_at")),
        toLocalDateTime(rs.getTimestamp("last_analyzed_at")),
        rs.getString("sync_error_message"),
        rs.getString("analysis_error_message")
    );

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
