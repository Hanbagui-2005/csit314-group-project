package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// PM-01 - manages IDP categories and platform-wide reporting, separate from User Admin
@Entity
@Table(name = "platform_managers")
public class PlatformManager extends UserAccount {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    protected PlatformManager() {
    }

    public PlatformManager(String fullName, String email, String passwordHash) {
        super(Role.PLATFORM_MANAGER, email, passwordHash, AccountStatus.ACTIVE);
        this.fullName = fullName;
    }

    @Override
    public String getDisplayName() {
        return fullName;
    }

    public String getFullName() {
        return fullName;
    }
}
