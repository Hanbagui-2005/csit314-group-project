package com.csit314.platform.dto;

// PM-10 - a single project ranked by its own views + shortlists, so PM can see which
// individual projects might be worth featuring
public record TopProject(Long projectId, String title, String companyName, long viewCount, long shortlistCount) {
}
