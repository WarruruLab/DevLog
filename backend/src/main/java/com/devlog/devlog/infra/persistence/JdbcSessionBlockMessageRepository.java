package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.analysis.SessionBlockMessage;
import com.devlog.devlog.domain.analysis.SessionBlockMessageRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionBlockMessageRepository implements SessionBlockMessageRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionBlockMessageRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<SessionBlockMessage> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO session_block_message (
                session_id, block_id, message_id, message_order, created_at
            ) VALUES (
                :sessionId, :blockId, :messageId, :messageOrder, :createdAt
            )
            """;

        var batchParams = mappings.stream()
            .map(m -> new MapSqlParameterSource()
                .addValue("sessionId", m.getSessionId())
                .addValue("blockId", m.getBlockId())
                .addValue("messageId", m.getMessageId())
                .addValue("messageOrder", m.getMessageOrder())
                .addValue("createdAt", toTimestamp(m.getCreatedAt())))
            .toArray(MapSqlParameterSource[]::new);

        namedTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public List<SessionBlockMessage> findAllBySessionId(String sessionId) {
        String sql = """
            SELECT *
            FROM session_block_message
            WHERE session_id = ?
            ORDER BY block_id ASC, message_order ASC, id ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, sessionId);
    }

    @Override
    public List<SessionBlockMessage> findByMessageId(String sessionId, String messageId) {
        String sql = """
            SELECT *
            FROM session_block_message
            WHERE session_id = ?
              AND message_id = ?
            ORDER BY block_id ASC, message_order ASC, id ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, sessionId, messageId);
    }

    @Override
    public boolean existsBlockMessage(Long blockId, String messageId) {
        String sql = """
            SELECT COUNT(*)
            FROM session_block_message
            WHERE block_id = ?
              AND message_id = ?
            """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, blockId, messageId);
        return count != null && count > 0;
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        jdbcTemplate.update("DELETE FROM session_block_message WHERE session_id = ?", sessionId);
    }

    private final RowMapper<SessionBlockMessage> rowMapper = (rs, rowNum) -> new SessionBlockMessage(
        rs.getLong("id"),
        rs.getString("session_id"),
        rs.getLong("block_id"),
        rs.getString("message_id"),
        rs.getInt("message_order"),
        toLocalDateTime(rs.getTimestamp("created_at"))
    );

    private static Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
