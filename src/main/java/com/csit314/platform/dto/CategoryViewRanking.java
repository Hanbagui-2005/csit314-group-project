package com.csit314.platform.dto;

// PM-04 - a category and the total view count across all of its projects
public record CategoryViewRanking(Long categoryId, String categoryName, long totalViews) {
}
