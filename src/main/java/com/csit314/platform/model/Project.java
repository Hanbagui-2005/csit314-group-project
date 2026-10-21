package com.csit314.platform.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// IDR-04 / IDR-05: a designer's portfolio project, with one or more photos
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(optional = false)
    @JoinColumn(name = "designer_id", nullable = false)
    private InteriorDesigner designer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjectStatus status = ProjectStatus.CURRENT;

    // eager: every page that shows a project needs its photos, and the list is always small (max 10)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_photos", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_url", nullable = false)
    private List<String> photoUrls = new ArrayList<>();

    @Column(nullable = false)
    private int viewCount = 0;

    @Column(nullable = false)
    private int shortlistCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Project() {
    }

    public Project(String title, String description, Category category, InteriorDesigner designer) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.designer = designer;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addPhoto(String url) {
        photoUrls.add(url);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public InteriorDesigner getDesigner() {
        return designer;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getShortlistCount() {
        return shortlistCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
