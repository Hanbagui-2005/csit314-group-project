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
    // category keeps working; only active categories are offered for new projects.
    //
    // Anthony's review - active = true is only a Java field initialiser, which never
    // applies to a column added by ddl-auto=update on an existing MySQL database:
    // Hibernate runs "alter table categories add column active bit not null" and
    // MySQL fills every existing row with 0, deactivating every category that was
    // already there. The explicit DB-level default fixes that for anyone upgrading
    @Column(nullable = false, columnDefinition = "boolean default true")
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

    // PM-13 - adding a category with the same name as a deactivated one reactivates it
    // instead of erroring or creating a duplicate; this is the only way back since
    // there's no separate reactivate button
    public void activate() {
        this.active = true;
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
