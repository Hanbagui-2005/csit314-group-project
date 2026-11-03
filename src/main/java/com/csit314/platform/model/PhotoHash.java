package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// PM-04 - a perceptual (average) hash of one uploaded project photo, kept so every new
// upload can be compared against every photo already on the platform
@Entity
@Table(name = "photo_hashes")
public class PhotoHash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "photo_url", nullable = false, length = 300)
    private String photoUrl;

    @Column(nullable = false)
    private long hash;

    protected PhotoHash() {
    }

    public PhotoHash(Project project, String photoUrl, long hash) {
        this.project = project;
        this.photoUrl = photoUrl;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public long getHash() {
        return hash;
    }
}
