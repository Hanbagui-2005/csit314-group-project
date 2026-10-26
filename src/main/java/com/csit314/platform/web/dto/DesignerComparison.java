package com.csit314.platform.web.dto;

import com.csit314.platform.model.InteriorDesigner;

import java.util.List;

// CUS-06 - no rating field yet, that needs CUS-13 which doesn't exist; the page just
// shows "no ratings yet" as static text instead of a fake number
public record DesignerComparison(InteriorDesigner designer, Integer priceMin, Integer priceMax, List<String> styles) {
}
