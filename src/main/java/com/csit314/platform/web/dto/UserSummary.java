package com.csit314.platform.web.dto;

// one row in the admin user list (ADM-02)
public record UserSummary(Long id, String name, String email, String role, String status) {
}
