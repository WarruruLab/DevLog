package com.devlog.devlog.api.controller.draft;

import com.devlog.devlog.api.dto.request.CreateDraftRequest;
import com.devlog.devlog.api.dto.response.DraftResponse;
import com.devlog.devlog.service.draft.DraftQueryService;
import com.devlog.devlog.service.draft.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devlog/drafts")
public class DraftController {

    private final DraftService draftService;
    private final DraftQueryService draftQueryService;

    public DraftController(DraftService draftService, DraftQueryService draftQueryService) {
        this.draftService = draftService;
        this.draftQueryService = draftQueryService;
    }

    @PostMapping
    public ResponseEntity<DraftResponse> createDraft(@RequestBody CreateDraftRequest request) {
        DraftResponse response = draftService.createDraft(request.sessionId(), request.selectedBlockIds());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{draftId}")
    public ResponseEntity<DraftResponse> getDraft(@PathVariable Long draftId) {
        return ResponseEntity.ok(draftQueryService.getDraft(draftId));
    }
}
