package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftRepository;
import java.sql.PreparedStatement;
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
        // 1. SQL 문에 id = LAST_INSERT_ID(id)를 추가하여 업데이트 시에도 ID가 유지되도록 함
        String sql = "INSERT INTO draft (session_id, content) VALUES (?, ?) " +
            "ON DUPLICATE KEY UPDATE content = VALUES(content), id = LAST_INSERT_ID(id)";

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();

        // 2. PreparedStatementCreator를 사용하여 자동 생성된 키를 받을 준비를 함
        jdbcTemplate.update(con -> {
            // 두 번째 인자로 자동 생성되는 컬럼명("id")을 명시
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, draft.getSessionId());
            ps.setString(2, draft.getContent());
            return ps;
        }, keyHolder);

        // 3. keyHolder에서 생성된(혹은 업데이트된) 키를 꺼내서 반환
        Number key = keyHolder.getKey();
        return (key != null) ? key.longValue() : null;
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
        rs.getString("content"),
        rs.getTimestamp("created_at").toLocalDateTime()
    );
}
