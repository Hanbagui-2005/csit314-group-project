package com.csit314.platform.dto;

// PM-07 - active designer supply vs. customer booking demand for one category
public record CategoryDesignerCoverage(Long categoryId, String categoryName, long activeDesignerCount,
                                       long demandCount, boolean shortage) {
}
