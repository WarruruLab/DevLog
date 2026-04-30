package com.devlog.devlog.service.draft;

import com.devlog.devlog.api.dto.response.DraftResponse;
import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftBlock;
import com.devlog.devlog.domain.draft.DraftBlockRepository;
import com.devlog.devlog.domain.draft.DraftRepository;
import com.devlog.devlog.domain.session.LogicalSession;
import com.devlog.devlog.domain.session.LogicalSessionRepository;
import com.devlog.devlog.service.llm.AiService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {

    private final SessionBlockRepository blockRepository;
    private final DraftRepository draftRepository;
    private final DraftBlockRepository draftBlockRepository;
    private final LogicalSessionRepository sessionRepository;
    private final AiService aiService;
    private final int maxSelectedBlocks;

    public DraftService(SessionBlockRepository blockRepository,
        DraftRepository draftRepository,
        DraftBlockRepository draftBlockRepository,
        LogicalSessionRepository sessionRepository,
        AiService aiService,
        @Value("${draft.max-selected-blocks:200}") int maxSelectedBlocks) {
        this.blockRepository = blockRepository;
        this.draftRepository = draftRepository;
        this.draftBlockRepository = draftBlockRepository;
        this.sessionRepository = sessionRepository;
        this.aiService = aiService;
        this.maxSelectedBlocks = maxSelectedBlocks;
    }

    @Transactional
    public DraftResponse createDraft(String sessionId, List<Long> selectedBlockIds) {
        LogicalSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("세션을 찾을 수 없다: " + sessionId));

        List<Long> requestedBlockIds = selectedBlockIds == null ? List.of() : selectedBlockIds;
        if (requestedBlockIds.size() > maxSelectedBlocks) {
            throw new IllegalArgumentException("selectedBlockIds exceeds maxSelectedBlocks: " + maxSelectedBlocks);
        }
        List<SessionBlock> selectedBlocks = orderedSelectedBlocks(sessionId, requestedBlockIds);
        int versionNo = draftRepository.nextVersion(sessionId);
        LocalDateTime now = LocalDateTime.now();
        String title = defaultString(session.getTitle(), sessionId);

        if (requestedBlockIds.isEmpty() || selectedBlocks.size() != requestedBlockIds.size()) {
            String failureMessage = "선택한 블록이 없거나 일부 블록을 찾을 수 없다.";
            Draft failedDraft = new Draft(
                null,
                sessionId,
                versionNo,
                "FAILED",
                title,
                null,
                failureMessage,
                now,
                now
            );
            Long draftId = draftRepository.save(failedDraft);
            return new DraftResponse(
                draftId,
                sessionId,
                versionNo,
                "FAILED",
                title,
                null,
                requestedBlockIds,
                now
            );
        }

        String systemPrompt = """
            너는 개발로그 초안을 작성하는 도우미이다.
            사용자가 선택한 블록만 입력으로 받으며, 결과는 Markdown 형식의 블로그 초안이어야 한다.
            - 설명은 자연스럽고 간결하게 작성한다.
            - 선택된 블록의 흐름을 유지한다.
            - 불필요한 메타 설명은 줄인다.
            """;

        String userPrompt = formatBlocksForAi(selectedBlocks);

        try {
            String content = aiService.ask(systemPrompt, userPrompt);

            Draft draft = new Draft(
                null,
                sessionId,
                versionNo,
                "COMPLETED",
                title,
                content,
                systemPrompt + "\n\n" + userPrompt,
                now,
                now
            );
            Long draftId = draftRepository.save(draft);
            draftBlockRepository.saveAll(buildDraftBlocks(draftId, requestedBlockIds));

            return new DraftResponse(
                draftId,
                sessionId,
                versionNo,
                "COMPLETED",
                title,
                content,
                requestedBlockIds,
                now
            );
        } catch (Exception e) {
            String failureMessage = safeMessage(e);
            Draft failedDraft = new Draft(
                null,
                sessionId,
                versionNo,
                "FAILED",
                title,
                null,
                failureMessage,
                now,
                now
            );
            Long draftId = draftRepository.save(failedDraft);
            return new DraftResponse(
                draftId,
                sessionId,
                versionNo,
                "FAILED",
                title,
                null,
                requestedBlockIds,
                now
            );
        }
    }

    private List<SessionBlock> orderedSelectedBlocks(String sessionId, List<Long> selectedBlockIds) {
        if (selectedBlockIds == null || selectedBlockIds.isEmpty()) {
            return List.of();
        }

        List<SessionBlock> blocks = blockRepository.findByIds(sessionId, selectedBlockIds);
        Map<Long, SessionBlock> blockById = blocks.stream()
            .filter(block -> block.getBlockId() != null)
            .collect(Collectors.toMap(
                SessionBlock::getBlockId,
                block -> block,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<SessionBlock> ordered = new ArrayList<>(selectedBlockIds.size());
        for (Long blockId : selectedBlockIds) {
            SessionBlock block = blockById.get(blockId);
            if (block != null) {
                ordered.add(block);
            }
        }
        return ordered;
    }

    private List<DraftBlock> buildDraftBlocks(Long draftId, List<Long> selectedBlockIds) {
        List<DraftBlock> draftBlocks = new ArrayList<>(selectedBlockIds.size());
        for (int i = 0; i < selectedBlockIds.size(); i++) {
            draftBlocks.add(new DraftBlock(
                null,
                draftId,
                selectedBlockIds.get(i),
                i + 1,
                LocalDateTime.now()
            ));
        }
        return draftBlocks;
    }

    private String formatBlocksForAi(List<SessionBlock> blocks) {
        return blocks.stream()
            .sorted(Comparator.comparingInt(block -> block.getSequenceNo() != null ? block.getSequenceNo() : Integer.MAX_VALUE))
            .map(block -> String.format(
                "### %s\n- type: %s\n- summary: %s\n```json\n%s\n```",
                defaultString(block.getTitle(), "Untitled"),
                defaultString(block.getBlockType(), "PROBLEM"),
                defaultString(block.getSummary(), ""),
                defaultString(block.getContentJson(), "{}")
            ))
            .collect(Collectors.joining("\n\n"));
    }

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
