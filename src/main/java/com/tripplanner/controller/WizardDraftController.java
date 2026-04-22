package com.tripplanner.controller;

import com.tripplanner.service.WizardDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/wizard-draft")
@RequiredArgsConstructor
public class WizardDraftController {

    private final WizardDraftService wizardDraftService;

    @GetMapping
    public ResponseEntity<String> getDraft() {
        return wizardDraftService.getDraft()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveDraft(@RequestBody String draftJson) {
        wizardDraftService.saveDraft(draftJson);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft() {
        wizardDraftService.deleteDraft();
    }
}
