package com.csit314.platform.service;

import com.csit314.platform.model.BookingStatus;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.web.dto.DesignerQualitySummary;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

// ADM-11 - who's generating the most complaints/cancellations relative to their booking volume
@Service
public class AdminInsightsService {

    private final InteriorDesignerRepository designerRepository;
    private final ComplaintRepository complaintRepository;
    private final BookingRepository bookingRepository;

    public AdminInsightsService(InteriorDesignerRepository designerRepository,
                                ComplaintRepository complaintRepository,
                                BookingRepository bookingRepository) {
        this.designerRepository = designerRepository;
        this.complaintRepository = complaintRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<DesignerQualitySummary> designerQualitySummaries() {
        return designerRepository.findAll().stream()
                .map(this::summarise)
                // highest dispute rate first, nulls (no bookings at all) go last
                .sorted(Comparator.comparing(DesignerQualitySummary::disputeRate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private DesignerQualitySummary summarise(InteriorDesigner designer) {
        long complaints = complaintRepository.countByDesignerId(designer.getId());
        long totalBookings = bookingRepository.countByDesignerId(designer.getId());
        long cancelled = bookingRepository.countByDesignerIdAndStatus(designer.getId(), BookingStatus.CANCELLED);

        Double disputeRate = totalBookings == 0 ? null : (double) complaints / totalBookings;
        Double cancellationRate = totalBookings == 0 ? null : (double) cancelled / totalBookings;

        return new DesignerQualitySummary(designer, complaints, totalBookings, cancelled, disputeRate, cancellationRate);
    }
}
