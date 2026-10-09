package com.csit314.platform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer extends UserAccount {

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    protected Customer() {
    }

    // customers don't need approval, so they're active straight away
    public Customer(String fullName, String email, String passwordHash) {
        super(Role.CUSTOMER, email, passwordHash, AccountStatus.ACTIVE);
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
