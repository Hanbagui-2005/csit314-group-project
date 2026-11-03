package com.csit314.platform.dto;

// PM-14 - a metric whose value on the most recent complete day deviates significantly
// from its recent average. Deliberately yesterday, not today: today is still partway
// through, so comparing its partial count against a baseline built from complete days
// would flag a "drop" most mornings regardless of actual activity
public record AnomalyAlert(String metricLabel, long value, double recentAverage) {
}
