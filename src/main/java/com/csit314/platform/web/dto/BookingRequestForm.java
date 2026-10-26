package com.csit314.platform.web.dto;

import java.time.LocalDate;

// CUS-11 - notes and projectId are both optional
public record BookingRequestForm(LocalDate preferredDate, String notes, Long projectId) {
}
