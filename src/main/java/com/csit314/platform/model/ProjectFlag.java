package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// PM-11 - a project flagged for Platform Management's review, either by a customer
// reporting it or automatically (PM-04's plagiarism check)
@Entity
@Table(name = "project_flags")
public class ProjectFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // null means this flag was raised automatically by the system, not a customer
    @ManyToOne
    @JoinColumn(name = "reported_by_customer_id")
    private Customer reportedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectFlagStatus status = ProjectFlagStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProjectFlag() {
    }

    public ProjectFlag(Project project, Customer reportedBy, String reason) {
        this.project = project;
        this.reportedBy = reportedBy;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // only an OPEN flag can be decided - same reasoning as Complaint.resolve()/dismiss():
    // an admin shouldn't be able to flip a decided flag back and forth
    public void review() {
        if (status != ProjectFlagStatus.OPEN) {
            throw new IllegalStateException("This flag has already been decided.");
        }
        status = ProjectFlagStatus.REVIEWED;
    }

    public void dismiss() {
        if (status != ProjectFlagStatus.OPEN) {
            throw new IllegalStateException("This flag has already been decided.");
        }
        status = ProjectFlagStatus.DISMISSED;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public Customer getReportedBy() {
        return reportedBy;
    }

    public String getReason() {
        return reason;
    }

    public ProjectFlagStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
