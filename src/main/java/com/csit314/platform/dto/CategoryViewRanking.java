package com.csit314.platform.dto;

// PM-10 - a category and the total view count across all of its projects (this isn't
// PM-04, which is reverse-image-search for plagiarism, not view counts)
public record CategoryViewRanking(Long categoryId, String categoryName, long totalViews) {
}
