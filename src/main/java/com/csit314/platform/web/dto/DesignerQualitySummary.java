package com.csit314.platform.web.dto;

import com.csit314.platform.model.InteriorDesigner;

// ADM-11 - rates, not raw counts, so designers with different booking volumes are
// comparable. Rates are null when the designer has no bookings at all (nothing to rate)
public record DesignerQualitySummary(InteriorDesigner designer, long complaintCount, long totalBookings,
                                     long cancelledBookings, Double disputeRate, Double cancellationRate) {
}
