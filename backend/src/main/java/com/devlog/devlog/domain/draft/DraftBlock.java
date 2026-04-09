package com.devlog.devlog.domain.draft;

import java.time.LocalDateTime;

public class DraftBlock {
    private final Long id;
    private final Long draftId;
    private final Long blockId;
    private final Integer selectedOrder;
    private final LocalDateTime createdAt;

    public DraftBlock(Long id, Long draftId, Long blockId, Integer selectedOrder,
        LocalDateTime createdAt) {
        this.id = id;
        this.draftId = draftId;
        this.blockId = blockId;
        this.selectedOrder = selectedOrder;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDraftId() {
        return draftId;
    }

    public Long getBlockId() {
        return blockId;
    }

    public Integer getSelectedOrder() {
        return selectedOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
