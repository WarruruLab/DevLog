package com.devlog.devlog.service.draft;

import com.devlog.devlog.domain.analysis.SessionBlock;
import com.devlog.devlog.domain.analysis.SessionBlockRepository;
import com.devlog.devlog.domain.draft.Draft;
import com.devlog.devlog.domain.draft.DraftRepository;
import com.devlog.devlog.domain.llm.LlmMessage;
import com.devlog.devlog.domain.llm.LlmOptions;
import com.devlog.devlog.domain.llm.LlmRequest;
import com.devlog.devlog.domain.llm.LlmRole;
import com.devlog.devlog.service.llm.AiService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {

    private final SessionBlockRepository blockRepository;
    private final DraftRepository draftRepository;
    private final AiService aiService;

    public DraftService(SessionBlockRepository blockRepository,
        DraftRepository draftRepository,
        AiService aiService) {
        this.blockRepository = blockRepository;
        this.draftRepository = draftRepository;
        this.aiService = aiService;
    }

    @Transactional
    public Long createDraft(String sessionId) {
        // 1. 분석된 블록 조회 (sessionId는 String/UUID)
        List<SessionBlock> blocks = blockRepository.findAllBySessionId(sessionId);
        if (blocks.isEmpty()) {
            throw new RuntimeException("해당 세션에 분석된 블록이 없습니다. 세션 ID: " + sessionId);
        }

        // 2. 컨텍스트 조립
        String context = formatBlocksForAi(blocks);

        // 3. 시스템 프롬프트 설정
        String systemPrompt = """
            너는 숙련된 시니어 개발자이자 기술 블로그 에디터야.
            제공된 JSON 형식의 개발 세션 분석 결과를 바탕으로 기술 블로그 포스팅 초안을 작성해줘.
            
            지시사항:
            1. 각 블록의 트러블슈팅 과정과 기술적 결정 사항을 상세히 포함할 것.
            2. 독자들이 인사이트를 얻을 수 있도록 전문적인 문체로 작성할 것.
            3. 결과물은 반드시 Markdown 형식이어야 함.
            4. 서론은 생략하고 바로 본론부터 시작할 것.
            """;

        // 4. AiService 호출
        LlmRequest request = new LlmRequest(
            systemPrompt,
            List.of(new LlmMessage(LlmRole.USER, context)),
            LlmOptions.defaults()
        );
        String content = aiService.ask(request);

        // 5. 초안 저장
        Draft draft = new Draft(null, sessionId, content, LocalDateTime.now());
        return draftRepository.save(draft);
    }

    private String formatBlocksForAi(List<SessionBlock> blocks) {
        return blocks.stream()
            .map(block -> String.format("### %s\n```json\n%s\n```",
                block.getTitle(), block.getContentJson()))
            .collect(Collectors.joining("\n\n"));
    }
}
