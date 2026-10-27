package com.csit314.platform.web.dto;

// PM-05 - activity counts across the platform for one reporting window
public record PlatformReport(String periodLabel, long newCustomers, long newDesigners,
                              long newProjects, long newBookings, long newComplaints) {
}
