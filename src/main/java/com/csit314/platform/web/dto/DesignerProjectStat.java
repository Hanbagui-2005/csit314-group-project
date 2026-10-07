package com.csit314.platform.web.dto;

/**
 * Placeholder view model for a project row on the designer's own dashboard,
 * including its view/shortlist counters (IDR-10).
 * Temporary mock data until Role 3 wires this to a real IDP entity/service.
 */
public record DesignerProjectStat(Long id, String title, String imageUrl, int views, int shortlists) {
}
