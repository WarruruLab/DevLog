package com.devlog.devlog.api.controller.draft;

import com.devlog.devlog.api.dto.request.CreateDraftRequest;
import com.devlog.devlog.api.dto.response.DraftResponse;
import com.devlog.devlog.service.draft.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    public ResponseEntity<DraftResponse> createDraft(@RequestBody CreateDraftRequest request) {
        DraftResponse response = draftService.createDraft(request.sessionId(), request.selectedBlockIds());
        return ResponseEntity.ok(response);
    }
}
