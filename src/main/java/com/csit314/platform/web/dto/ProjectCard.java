package com.csit314.platform.web.dto;

/**
 * Placeholder view model for a project card shown in search results and listings.
 * Temporary mock data until Role 3 wires this to a real IDP entity/service.
 */
public record ProjectCard(Long id, String title, String designerName, String imageUrl, String category) {
}
