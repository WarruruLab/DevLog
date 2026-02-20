package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
        String sql = "INSERT INTO session_block (session_id, title, content_json) VALUES (?, ?, ?)";
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(con -> {
            // schema.sql에 정의된 PK 컬럼명인 "block_id"를 지정합니다.
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"block_id"});
            ps.setString(1, block.getSessionId());
            ps.setString(2, block.getTitle());
            ps.setString(3, block.getContentJson());
            return ps;
        }, keyHolder);

        // 생성된 block_id를 Number 타입으로 받아 Long으로 변환하여 반환합니다.
        Number key = keyHolder.getKey();
        return (key != null) ? key.longValue() : null;
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
