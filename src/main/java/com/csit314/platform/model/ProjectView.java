package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// IDR-08 - one row per project-detail page load. Project.viewCount is still kept as a
// running total (other pages already read it), this is just so a weekly delta is
// actually computable - a plain int can't tell you how many of those views were recent
@Entity
@Table(name = "project_views")
public class ProjectView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private LocalDateTime viewedAt;

    protected ProjectView() {
    }

    public ProjectView(Project project) {
        this.project = project;
    }

    @PrePersist
    void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }
}
