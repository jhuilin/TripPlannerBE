package com.tripplanner.repository;

import com.tripplanner.entity.WizardDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WizardDraftRepository extends JpaRepository<WizardDraft, Long> {
    Optional<WizardDraft> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM WizardDraft w WHERE w.userId = :userId")
    void deleteByUserId(Long userId);
}
