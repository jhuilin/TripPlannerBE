package com.tripplanner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wizard_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WizardDraft {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String draftJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
