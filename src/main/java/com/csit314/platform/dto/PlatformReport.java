package com.csit314.platform.dto;

// PM-06/PM-08 - activity counts across the platform for one reporting window
public record PlatformReport(String periodLabel, long newCustomers, long newDesigners,
                              long newProjects, long newBookings, long newComplaints) {
}
