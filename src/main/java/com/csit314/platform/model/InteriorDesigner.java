package com.csit314.platform.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "interior_designers")
public class InteriorDesigner extends UserAccount {

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // filled in when an admin approves or rejects the registration
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // CUS-09 - true once an admin approves a registration that included a license
    // number, or an admin manually toggles it later (ADM-05 - e.g. a designer added
    // their license after approval, or admin found a reason to revoke it)
    @Column(nullable = false)
    private boolean verified = false;

    protected InteriorDesigner() {
    }

    // new designers wait for admin approval before they can log in
    public InteriorDesigner(String contactName, String email, String passwordHash, Company company) {
        super(Role.INTERIOR_DESIGNER, email, passwordHash, AccountStatus.PENDING);
        this.contactName = contactName;
        this.company = company;
    }

    public void approve() {
        setStatus(AccountStatus.ACTIVE);
        this.rejectionReason = null;
        this.reviewedAt = LocalDateTime.now();
        this.verified = company.getLicenseNumber() != null && !company.getLicenseNumber().isBlank();
    }

    public void reject(String reason) {
        setStatus(AccountStatus.REJECTED);
        this.rejectionReason = reason;
        this.reviewedAt = LocalDateTime.now();
    }

    // the admin user list shows the studio name for designers
    @Override
    public String getDisplayName() {
        return company.getName();
    }

    public String getContactName() {
        return contactName;
    }

    public Company getCompany() {
        return company;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isVerified() {
        return verified;
    }

    // ADM-05
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
