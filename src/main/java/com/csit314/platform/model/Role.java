package com.csit314.platform.model;

public enum Role {
    ADMIN("User Admin"),
    INTERIOR_DESIGNER("Interior Designer"),
    CUSTOMER("Customer"),
    PLATFORM_MANAGER("Platform Management");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
