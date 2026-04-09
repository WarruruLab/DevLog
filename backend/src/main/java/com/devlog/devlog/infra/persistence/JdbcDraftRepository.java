package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftRepository;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDraftRepository implements DraftRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcDraftRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long save(Draft draft) {
        String sql = """
            INSERT INTO draft (
                session_id, version_no, status, title, content_markdown,
                generation_prompt, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        int versionNo = draft.getVersionNo() != null ? draft.getVersionNo() : nextVersion(draft.getSessionId());
        String status = defaultString(draft.getStatus(), "PENDING");
        String title = defaultString(draft.getTitle(), draft.getSessionId());
        String contentMarkdown = draft.getContentMarkdown() != null ? draft.getContentMarkdown() : draft.getContent();
        Timestamp createdAt = draft.getCreatedAt() != null ? Timestamp.valueOf(draft.getCreatedAt()) : Timestamp.valueOf(java.time.LocalDateTime.now());
        Timestamp updatedAt = draft.getUpdatedAt() != null ? Timestamp.valueOf(draft.getUpdatedAt()) : createdAt;

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"draft_id"});
            ps.setString(1, draft.getSessionId());
            ps.setInt(2, versionNo);
            ps.setString(3, status);
            ps.setString(4, title);
            ps.setString(5, contentMarkdown);
            ps.setString(6, draft.getGenerationPrompt());
            ps.setTimestamp(7, createdAt);
            ps.setTimestamp(8, updatedAt);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    @Override
    public Optional<Draft> findBySessionId(String sessionId) {
        return findLatestBySessionId(sessionId);
    }

    @Override
    public Optional<Draft> findLatestBySessionId(String sessionId) {
        String sql = """
            SELECT *
            FROM draft
            WHERE session_id = ?
            ORDER BY version_no DESC, draft_id DESC
            LIMIT 1
            """;
        try {
            Draft draft = jdbcTemplate.queryForObject(sql, draftRowMapper, sessionId);
            return Optional.ofNullable(draft);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Draft> findByDraftId(Long draftId) {
        String sql = "SELECT * FROM draft WHERE draft_id = ?";
        try {
            Draft draft = jdbcTemplate.queryForObject(sql, draftRowMapper, draftId);
            return Optional.ofNullable(draft);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public int nextVersion(String sessionId) {
        Integer next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_no), 0) + 1 FROM draft WHERE session_id = ?",
            Integer.class,
            sessionId
        );
        return next != null ? next : 1;
    }

    @Override
    public void deleteAllBySessionId(String sessionId) {
        jdbcTemplate.update("DELETE FROM draft WHERE session_id = ?", sessionId);
    }

    private final RowMapper<Draft> draftRowMapper = (rs, rowNum) -> new Draft(
        rs.getLong("draft_id"),
        rs.getString("session_id"),
        rs.getInt("version_no"),
        rs.getString("status"),
        rs.getString("title"),
        rs.getString("content_markdown"),
        rs.getString("generation_prompt"),
        toLocalDateTime(rs.getTimestamp("created_at")),
        toLocalDateTime(rs.getTimestamp("updated_at"))
    );

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
