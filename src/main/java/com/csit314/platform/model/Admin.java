package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "admins")
public class Admin extends UserAccount {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    protected Admin() {
    }

    public Admin(String fullName, String email, String passwordHash) {
        super(Role.ADMIN, email, passwordHash, AccountStatus.ACTIVE);
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
