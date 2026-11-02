package com.csit314.platform.dto;

// CUS-12 - budget range for a category, worked out from listings that gave one
public record BudgetEstimate(int min, int max, int sampleSize) {
}
