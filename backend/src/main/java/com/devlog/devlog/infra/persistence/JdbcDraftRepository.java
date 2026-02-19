package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftRepository;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    public void save(Draft draft) {
        String sql = "INSERT INTO draft (session_id, content) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE content = VALUES(content)";
        jdbcTemplate.update(sql, draft.getSessionId(), draft.getContent());
    }

    @Override
    public Optional<Draft> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM draft WHERE session_id = ?";
        try {
            Draft draft = jdbcTemplate.queryForObject(sql, draftRowMapper, sessionId);
            return Optional.ofNullable(draft);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private final RowMapper<Draft> draftRowMapper = (rs, rowNum) -> new Draft(
        rs.getLong("id"),
        rs.getString("session_id"),
        rs.getString("content")
    );
}
