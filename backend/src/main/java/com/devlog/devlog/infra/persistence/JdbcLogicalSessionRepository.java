package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
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

    // 1. 세션 저장
    @Override
    public void save(LogicalSession session) {
        // 중복 저장 시 에러를 방지하기 위해 INSERT IGNORE 사용을 추천합니다.
        String sql = "INSERT IGNORE INTO logical_session (session_id) VALUES (?)";
        jdbcTemplate.update(sql, session.getSessionId());
    }

    // 2. 특정 세션 조회
    @Override
    public Optional<LogicalSession> findBySessionId(String sessionId) {
        String sql = "SELECT session_id FROM logical_session WHERE session_id = ?";
        try {
            LogicalSession session = jdbcTemplate.queryForObject(sql, logicalSessionRowMapper, sessionId);
            return Optional.ofNullable(session);
        } catch (EmptyResultDataAccessException e) {
            // 결과가 없을 때 예외가 발생하므로 Optional.empty()를 반환합니다.
            return Optional.empty();
        }
    }

    // 3. 모든 세션 조회
    @Override
    public List<LogicalSession> findAll() {
        String sql = "SELECT session_id FROM logical_session";
        return jdbcTemplate.query(sql, logicalSessionRowMapper);
    }

    // 4. 세션 삭제
    @Override
    public void deleteById(String sessionId) {
        String sql = "DELETE FROM logical_session WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }

    // 공통 RowMapper
    private final RowMapper<LogicalSession> logicalSessionRowMapper = (rs, rowNum) ->
        new LogicalSession(rs.getString("session_id"));
}
