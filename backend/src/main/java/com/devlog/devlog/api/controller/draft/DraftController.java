package com.devlog.devlog.api.controller.draft;

import com.devlog.devlog.service.draft.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }
    // 블로그 초안 생성
    @PostMapping("/{sessionId}")
    public ResponseEntity<Long> createDraft(@PathVariable String sessionId) {
        Long draftId = draftService.createDraft(sessionId);
        return ResponseEntity.ok(draftId);
    }

//    //생성된 초안을 조회합니다.
//    @GetMapping("/{sessionId}")
//    public ResponseEntity<DraftResponse> getDraft(@PathVariable String sessionId) {
//        // Draft조회 로직 추가 시 구현
//    }

}
