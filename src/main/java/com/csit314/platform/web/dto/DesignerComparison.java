package com.csit314.platform.web.dto;

import com.csit314.platform.model.InteriorDesigner;

import java.util.List;

// CUS-06 - averageRating is null when the designer has no ratings yet (CUS-13)
public record DesignerComparison(InteriorDesigner designer, Integer priceMin, Integer priceMax,
                                 List<String> styles, Double averageRating) {
}
