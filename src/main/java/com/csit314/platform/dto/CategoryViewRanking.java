package com.csit314.platform.dto;

// extra, not its own tracked story - a category and the total view count across all of
// its projects. Not PM-04 (reverse-image-search for plagiarism) or PM-10 (that's
// top-performing designers/projects, not categories - see DesignerPerformanceRanking)
public record CategoryViewRanking(Long categoryId, String categoryName, long totalViews) {
}
