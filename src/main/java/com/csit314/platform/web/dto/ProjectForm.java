package com.csit314.platform.web.dto;

// IDR-14/CUS-04 - budgetMin/budgetMax/estimatedTimeline/roomSizeSqm are all optional
public record ProjectForm(String title, String description, Long categoryId,
                          Integer budgetMin, Integer budgetMax, String estimatedTimeline,
                          Integer roomSizeSqm) {
}
