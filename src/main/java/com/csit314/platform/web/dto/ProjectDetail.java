package com.csit314.platform.web.dto;

/**
 * Placeholder view model for a single project's detail page.
 * Temporary mock data until Role 3 wires this to a real IDP entity/service.
 */
public record ProjectDetail(Long id, String title, String description, String imageUrl,
                             String designerName, Long designerId, String category) {
}
