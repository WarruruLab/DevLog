package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSessionBlockRepository implements SessionBlockRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcSessionBlockRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long save(SessionBlock block) {
        String sql = """
            INSERT INTO session_block (
                session_id, sequence_no, block_type, title, summary, content_json,
                status, source_message_count, message_start_at, message_end_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        Integer sequenceNo = block.getSequenceNo() != null ? block.getSequenceNo() : nextSequenceNo(block.getSessionId());
        String blockType = defaultString(block.getBlockType(), "PROBLEM");
        String summary = defaultString(block.getSummary(), block.getTitle());
        String status = defaultString(block.getStatus(), "ACTIVE");
        Integer sourceMessageCount = block.getSourceMessageCount() != null ? block.getSourceMessageCount() : 0;

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"block_id"});
            ps.setString(1, block.getSessionId());
            ps.setInt(2, sequenceNo);
            ps.setString(3, blockType);
            ps.setString(4, block.getTitle());
            ps.setString(5, summary);
            ps.setString(6, block.getContentJson());
            ps.setString(7, status);
            ps.setInt(8, sourceMessageCount);
            ps.setTimestamp(9, toTimestamp(block.getMessageStartAt()));
            ps.setTimestamp(10, toTimestamp(block.getMessageEndAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public List<SessionBlock> findAllBySessionId(String sessionId) {
        String sql = """
            SELECT *
            FROM session_block
            WHERE session_id = ?
            ORDER BY sequence_no ASC, block_id ASC
            """;
        return jdbcTemplate.query(sql, sessionBlockRowMapper, sessionId);
    }

    @Override
    public List<SessionBlock> findByIds(String sessionId, List<Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return Collections.emptyList();
        }

        String sql = """
            SELECT *
            FROM session_block
            WHERE session_id = :sessionId
              AND block_id IN (:blockIds)
            ORDER BY sequence_no ASC, block_id ASC
            """;
        var params = new MapSqlParameterSource()
            .addValue("sessionId", sessionId)
            .addValue("blockIds", blockIds);
        return namedTemplate.query(sql, params, sessionBlockRowMapper);
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        jdbcTemplate.update("DELETE FROM session_block WHERE session_id = ?", sessionId);
    }

    @Override
    public void replaceAll(String sessionId, List<SessionBlock> blocks) {
        deleteBySessionId(sessionId);
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            SessionBlock block = blocks.get(i);
            Integer sequenceNo = block.getSequenceNo() != null ? block.getSequenceNo() : i + 1;
            save(new SessionBlock(
                block.getBlockId(),
                sessionId,
                sequenceNo,
                block.getBlockType(),
                block.getTitle(),
                block.getSummary(),
                block.getContentJson(),
                block.getStatus(),
                block.getSourceMessageCount(),
                block.getMessageStartAt(),
                block.getMessageEndAt()
            ));
        }
    }

    private int nextSequenceNo(String sessionId) {
        Integer next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM session_block WHERE session_id = ?",
            Integer.class,
            sessionId
        );
        return next != null ? next : 1;
    }

    private final RowMapper<SessionBlock> sessionBlockRowMapper = (rs, rowNum) -> new SessionBlock(
        rs.getLong("block_id"),
        rs.getString("session_id"),
        rs.getInt("sequence_no"),
        rs.getString("block_type"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("content_json"),
        rs.getString("status"),
        rs.getInt("source_message_count"),
        toLocalDateTime(rs.getTimestamp("message_start_at")),
        toLocalDateTime(rs.getTimestamp("message_end_at"))
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
