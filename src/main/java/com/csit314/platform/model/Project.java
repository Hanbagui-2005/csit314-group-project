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

// IDR-04/05 - a project in a designer's portfolio, can have multiple photos
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

    // had to make this eager, kept getting a LazyInitializationException when the page tried to render
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_photos", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_url", nullable = false)
    private List<String> photoUrls = new ArrayList<>();

    // IDR-07 - the "staged final shot" photos above are what customers see by default,
    // these are the optional before shots that go with them on the project edit page
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_before_photos", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "photo_order")
    @Column(name = "photo_url", nullable = false)
    private List<String> beforePhotoUrls = new ArrayList<>();

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

    public void addBeforePhoto(String url) {
        beforePhotoUrls.add(url);
    }

    public void updateDetails(String title, String description, Category category) {
        this.title = title;
        this.description = description;
        this.category = category;
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

    public List<String> getBeforePhotoUrls() {
        return beforePhotoUrls;
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
