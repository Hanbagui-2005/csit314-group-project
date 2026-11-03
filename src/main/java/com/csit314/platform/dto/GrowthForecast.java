package com.csit314.platform.dto;

import java.util.List;

// PM-02 - a simple moving-average projection for next month, based on the last few
// completed months of actual growth, so PM can plan capacity ahead of time
public record GrowthForecast(long projectedNewUsers, long projectedNewListings, List<MonthlyGrowth> history) {
}
