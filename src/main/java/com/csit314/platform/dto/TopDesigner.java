package com.csit314.platform.dto;

// PM-10 - a designer ranked by their portfolio's total views + shortlists, so PM can
// see who's performing well and might be worth featuring
public record TopDesigner(Long designerId, String companyName, long totalViews, long totalShortlists) {
}
