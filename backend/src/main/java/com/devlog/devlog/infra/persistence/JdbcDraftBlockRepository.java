package com.devlog.devlog.infra.persistence;

import com.devlog.devlog.domain.draft.DraftBlock;
import com.devlog.devlog.domain.draft.DraftBlockRepository;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDraftBlockRepository implements DraftBlockRepository {

    private final NamedParameterJdbcTemplate namedTemplate;
    private final JdbcTemplate jdbcTemplate;

    public JdbcDraftBlockRepository(NamedParameterJdbcTemplate namedTemplate, JdbcTemplate jdbcTemplate) {
        this.namedTemplate = namedTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<DraftBlock> draftBlocks) {
        if (draftBlocks == null || draftBlocks.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO draft_block (
                draft_id, block_id, selected_order, created_at
            ) VALUES (
                :draftId, :blockId, :selectedOrder, :createdAt
            )
            """;

        var batchParams = draftBlocks.stream()
            .map(d -> new MapSqlParameterSource()
                .addValue("draftId", d.getDraftId())
                .addValue("blockId", d.getBlockId())
                .addValue("selectedOrder", d.getSelectedOrder())
                .addValue("createdAt", toTimestamp(d.getCreatedAt())))
            .toArray(MapSqlParameterSource[]::new);

        namedTemplate.batchUpdate(sql, batchParams);
    }

    @Override
    public List<DraftBlock> findByDraftId(Long draftId) {
        String sql = """
            SELECT *
            FROM draft_block
            WHERE draft_id = ?
            ORDER BY selected_order ASC, id ASC
            """;
        return jdbcTemplate.query(sql, rowMapper, draftId);
    }

    @Override
    public void deleteByDraftId(Long draftId) {
        jdbcTemplate.update("DELETE FROM draft_block WHERE draft_id = ?", draftId);
    }

    private final RowMapper<DraftBlock> rowMapper = (rs, rowNum) -> new DraftBlock(
        rs.getLong("id"),
        rs.getLong("draft_id"),
        rs.getLong("block_id"),
        rs.getInt("selected_order"),
        toLocalDateTime(rs.getTimestamp("created_at"))
    );

    private static Timestamp toTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
