package com.devlog.devlog.domain.draft;

import java.util.List;

public interface DraftBlockRepository {
    void saveAll(List<DraftBlock> draftBlocks);

    List<DraftBlock> findByDraftId(Long draftId);

    void deleteByDraftId(Long draftId);
}
