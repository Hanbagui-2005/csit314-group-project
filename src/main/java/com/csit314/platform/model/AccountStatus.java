package com.csit314.platform.model;

public enum AccountStatus {
    // designers start as PENDING until a User Admin approves or rejects them
    PENDING("Pending"),
    ACTIVE("Active"),
    REJECTED("Rejected"),
    SUSPENDED("Suspended");

    private final String label;

    AccountStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
