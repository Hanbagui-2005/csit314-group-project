package com.csit314.platform.dto;

// IDR-03 - one row of the category breakdown: how many of the designer's projects are in
// this category, what share of the portfolio that is, and how much engagement (views,
// shortlists) that category's projects average - the actual ranking signal the story asks
// for, not just how many projects happen to be in the category
public record CategoryBreakdown(String categoryName, int projectCount, long percentage,
                                double averageViews, double averageShortlists) {
}
