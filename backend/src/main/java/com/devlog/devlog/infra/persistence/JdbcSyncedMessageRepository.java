package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSyncedMessageRepository implements SyncedMessageRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcSyncedMessageRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<SyncedMessage> messages) {
        String sql = """
            INSERT INTO session_message (
                message_id, session_id, role, author_name, content,
                message_created_at, structure_status, structured_at
            ) VALUES (
                :messageId, :sessionId, :role, :authorName, :content,
                :messageCreatedAt, :structureStatus, :structuredAt
            )
            """;

        SqlParameterSource[] batchParams = messages.stream()
            .map(m -> new MapSqlParameterSource()
                .addValue("messageId", m.getMessageId())
                .addValue("sessionId", m.getSessionId())
                .addValue("role", defaultString(m.getRole(), "USER"))
                .addValue("authorName", m.getAuthorName())
                .addValue("content", m.getContent())
                .addValue("messageCreatedAt", toTimestamp(m.getMessageCreatedAt()))
                .addValue("structureStatus", defaultString(m.getStructureStatus(), "PENDING"))
                .addValue("structuredAt", toTimestamp(m.getStructuredAt())))
            .toArray(SqlParameterSource[]::new);

        namedTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public Optional<LocalDateTime> findLastCreatedAtBySessionId(String sessionId) {
        String sql = "SELECT MAX(message_created_at) FROM session_message WHERE session_id = ?";
        try {
            Timestamp lastTime = jdbcTemplate.queryForObject(sql, Timestamp.class, sessionId);
            return Optional.ofNullable(lastTime).map(Timestamp::toLocalDateTime);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SyncedMessage> findAllBySessionId(String sessionId) {
        String sql = """
            SELECT *
            FROM session_message
            WHERE session_id = ?
            ORDER BY message_created_at ASC, id ASC
            """;
        return jdbcTemplate.query(sql, syncedMessageRowMapper, sessionId);
    }

    @Override
    public long countBySessionId(String sessionId) {
        return count("SELECT COUNT(*) FROM session_message WHERE session_id = ?", sessionId);
    }

    @Override
    public long countStructuredBySessionId(String sessionId) {
        return count("SELECT COUNT(*) FROM session_message WHERE session_id = ? AND structure_status = 'STRUCTURED'",
            sessionId);
    }

    @Override
    public long countUnstructuredBySessionId(String sessionId) {
        return count("SELECT COUNT(*) FROM session_message WHERE session_id = ? AND structure_status <> 'STRUCTURED'",
            sessionId);
    }

    @Override
    public void markStructured(List<String> messageIds, LocalDateTime structuredAt) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        String sql = """
            UPDATE session_message
            SET structure_status = 'STRUCTURED',
                structured_at = ?
            WHERE message_id = ?
            """;
        jdbcTemplate.batchUpdate(sql, messageIds, messageIds.size(), (ps, messageId) -> {
            ps.setTimestamp(1, toTimestamp(structuredAt));
            ps.setString(2, messageId);
        });
    }

    @Override
    public void deleteAllBySessionId(String sessionId) {
        jdbcTemplate.update("DELETE FROM session_message WHERE session_id = ?", sessionId);
    }

    private long count(String sql, String sessionId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, sessionId);
        return value != null ? value : 0L;
    }

    private final RowMapper<SyncedMessage> syncedMessageRowMapper = (rs, rowNum) -> new SyncedMessage(
        rs.getString("message_id"),
        rs.getString("session_id"),
        rs.getString("content"),
        rs.getString("role"),
        rs.getString("author_name"),
        toLocalDateTime(rs.getTimestamp("message_created_at")),
        rs.getString("structure_status"),
        toLocalDateTime(rs.getTimestamp("structured_at"))
    );

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
