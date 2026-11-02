package com.csit314.platform.web.dto;

import com.csit314.platform.model.Rating;

import java.util.List;

// CUS-06/CUS-13 - average is null when there are no ratings yet, not 0
public record RatingSummary(Double average, int count, List<Rating> reviews) {
}
