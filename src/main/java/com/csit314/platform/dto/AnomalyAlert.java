package com.csit314.platform.dto;

// PM-14 - a metric whose value today deviates significantly from its recent average
public record AnomalyAlert(String metricLabel, long todayValue, double recentAverage) {
}
