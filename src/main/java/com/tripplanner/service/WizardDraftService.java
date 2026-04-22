package com.tripplanner.service;

import com.tripplanner.entity.User;
import com.tripplanner.entity.WizardDraft;
import com.tripplanner.repository.WizardDraftRepository;
import com.tripplanner.security.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WizardDraftService {

    private final WizardDraftRepository wizardDraftRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional(readOnly = true)
    public Optional<String> getDraft() {
        return wizardDraftRepository.findByUserId(currentUserResolver.resolveId())
                .map(WizardDraft::getDraftJson);
    }

    @Transactional
    public void saveDraft(String draftJson) {
        Long userId = currentUserResolver.resolveId();
        WizardDraft draft = wizardDraftRepository.findByUserId(userId)
                .orElse(WizardDraft.builder().userId(userId).build());
        draft.setDraftJson(draftJson);
        wizardDraftRepository.save(draft);
    }

    @Transactional
    public void deleteDraft() {
        wizardDraftRepository.deleteByUserId(currentUserResolver.resolveId());
    }
}
