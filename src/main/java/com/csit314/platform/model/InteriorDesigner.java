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
}
