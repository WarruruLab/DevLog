package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
    public void save(SessionBlock block) {
        String sql = "INSERT INTO session_block (session_id, title, content_json) VALUES (?, ?, ?)";
        jdbcTemplate.update(sql, block.getSessionId(), block.getTitle(), block.getContentJson());
    }

    @Override
    public List<SessionBlock> findAllBySessionId(String sessionId) {
        String sql = "SELECT * FROM session_block WHERE session_id = ?";
        return jdbcTemplate.query(sql, sessionBlockRowMapper, sessionId);
    }

    private final RowMapper<SessionBlock> sessionBlockRowMapper = (rs, rowNum) -> new SessionBlock(
        rs.getLong("block_id"),
        rs.getString("session_id"),
        rs.getString("title"),
        rs.getString("content_json")
    );
}
