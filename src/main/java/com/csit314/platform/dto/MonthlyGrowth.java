package com.csit314.platform.dto;

// PM-02 - actual new-user/new-listing counts for one completed calendar month
public record MonthlyGrowth(String monthLabel, long newUsers, long newListings) {
}
