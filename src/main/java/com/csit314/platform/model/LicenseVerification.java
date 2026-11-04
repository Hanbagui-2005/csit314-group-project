package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "license_verifications",
        indexes = @Index(
                name = "idx_license_verification_designer_time",
                columnList = "designer_id, recorded_at"))
public class LicenseVerification {

    public enum Action {
        VERIFIED,
        REVOKED,
        LICENSE_CHANGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "designer_id", nullable = false, updatable = false)
    private InteriorDesigner designer;

    @Column(name = "license_number", length = 60, updatable = false)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Action action;

    @Column(name = "actor_email", nullable = false,
            length = 255, updatable = false)
    private String actorEmail;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String note;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    protected LicenseVerification() {
    }

    public LicenseVerification(
            InteriorDesigner designer,
            String licenseNumber,
            Action action,
            String actorEmail,
            String note) {
        this.designer = designer;
        this.licenseNumber = licenseNumber;
        this.action = action;
        this.actorEmail = actorEmail;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public InteriorDesigner getDesigner() {
        return designer;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public Action getAction() {
        return action;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
