package com.csit314.platform.web.dto;

/**
 * Placeholder view model for a row in the admin's "all registered users" table (ADM-02).
 * Temporary mock data until Role 3 wires this to a real user/account service.
 */
public record UserSummary(Long id, String name, String email, String role, String status) {
}
