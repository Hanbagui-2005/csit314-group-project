package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    // PM-13 - deactivated instead of removed, so a project that still references this
    // category keeps working; only active categories are offered for new projects
    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String name) {
        this.name = name;
    }

    // supports PM-01 - not its own tracked Taiga story
    public void rename(String name) {
        this.name = name;
    }

    // PM-13
    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
