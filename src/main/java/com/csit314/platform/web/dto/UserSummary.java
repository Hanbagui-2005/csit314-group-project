package com.csit314.platform.web.dto;

// ADM-02 data, hardcoded for now
public record UserSummary(Long id, String name, String email, String role, String status) {
}
