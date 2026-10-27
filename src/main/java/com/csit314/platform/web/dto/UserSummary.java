package com.csit314.platform.web.dto;

// one row in the admin user list (ADM-02). licenseNumber/verified/riskScore are
// only populated for Interior Designer rows (ADM-05/07), null otherwise
public record UserSummary(Long id, String name, String email, String role, String status,
                          boolean locked, String licenseNumber, Boolean verified, Integer riskScore) {
}
