package com.devlog.devlog.service.draft;

import com.devlog.devlog.api.dto.response.DraftResponse;
import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftBlock;
import com.devlog.devlog.domain.draft.DraftBlockRepository;
import com.devlog.devlog.domain.draft.DraftRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DraftQueryService {

    private final DraftRepository draftRepository;
    private final DraftBlockRepository draftBlockRepository;

    public DraftQueryService(DraftRepository draftRepository, DraftBlockRepository draftBlockRepository) {
        this.draftRepository = draftRepository;
        this.draftBlockRepository = draftBlockRepository;
    }

    @Transactional(readOnly = true)
    public DraftResponse getDraft(Long draftId) {
        Draft draft = draftRepository.findByDraftId(draftId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Draft not found: " + draftId
            ));

        List<Long> selectedBlockIds = draftBlockRepository.findByDraftId(draftId).stream()
            .sorted(Comparator.comparingInt(DraftQueryService::selectedOrder))
            .map(DraftBlock::getBlockId)
            .toList();

        return new DraftResponse(
            draft.getDraftId(),
            draft.getSessionId(),
            draft.getVersionNo(),
            draft.getStatus(),
            draft.getTitle(),
            draft.getContentMarkdown(),
            selectedBlockIds,
            draft.getCreatedAt()
        );
    }

    private static int selectedOrder(DraftBlock block) {
        return block.getSelectedOrder() != null ? block.getSelectedOrder() : Integer.MAX_VALUE;
    }
}
