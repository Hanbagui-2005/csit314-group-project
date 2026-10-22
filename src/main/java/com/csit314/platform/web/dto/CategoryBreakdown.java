package com.csit314.platform.web.dto;

// IDR-03 - one row of the category breakdown: how many of the designer's projects
// are in this category, and what share of the portfolio that is
public record CategoryBreakdown(String categoryName, int projectCount, long percentage) {
}
