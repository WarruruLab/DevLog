package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.sync.SyncedMessage;
import com.devlog.devlog.domain.sync.SyncedMessageRepository;
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
        String sql = "INSERT INTO synced_message (message_id, session_id, content, created_at) " +
            "VALUES (:messageId, :sessionId, :content, :createdAt)";

        SqlParameterSource[] batchParams = messages.stream()
            .map(m -> new MapSqlParameterSource()
                .addValue("messageId", m.getMessageId())
                .addValue("sessionId", m.getSessionId())
                .addValue("content", m.getContent())
                .addValue("createdAt", m.getCreatedAt()))
            .toArray(SqlParameterSource[]::new);

        namedTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public Optional<LocalDateTime> findLastCreatedAtBySessionId(String sessionId) {
        String sql = "SELECT MAX(created_at) FROM synced_message WHERE session_id = ?";
        try {
            LocalDateTime lastTime = jdbcTemplate.queryForObject(sql, LocalDateTime.class, sessionId);
            return Optional.ofNullable(lastTime);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SyncedMessage> findAllBySessionId(String sessionId) {
        String sql = "SELECT * FROM synced_message WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, syncedMessageRowMapper, sessionId);
    }

    @Override
    public void deleteAllBySessionId(String sessionId) {
        String sql = "DELETE FROM synced_message WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }

    private final RowMapper<SyncedMessage> syncedMessageRowMapper = (rs, rowNum) -> new SyncedMessage(
        rs.getString("message_id"),
        rs.getString("session_id"),
        rs.getString("content"),
        rs.getTimestamp("created_at").toLocalDateTime()
    );
}
