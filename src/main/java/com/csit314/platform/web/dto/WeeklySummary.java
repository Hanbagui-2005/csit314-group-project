package com.csit314.platform.web.dto;

// IDR-08 - a designer's portfolio activity over the last 7 days
public record WeeklySummary(long viewsThisWeek, long shortlistsThisWeek, long messagesThisWeek,
                            int totalProjects, int totalViewsAllTime, int totalShortlistsAllTime) {
}
