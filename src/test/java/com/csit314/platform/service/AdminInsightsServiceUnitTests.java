package com.csit314.platform.service;

import com.csit314.platform.model.BookingStatus;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.web.dto.DesignerQualitySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// ADM-11
@ExtendWith(MockitoExtension.class)
class AdminInsightsServiceUnitTests {

    @Mock InteriorDesignerRepository designerRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock BookingRepository bookingRepository;
    AdminInsightsService service;

    InteriorDesigner busy;
    InteriorDesigner quiet;

    @BeforeEach
    void setUp() {
        service = new AdminInsightsService(designerRepository, complaintRepository, bookingRepository);

        busy = new InteriorDesigner("Busy", "busy@example.test", "encoded", new Company("Busy Studio", "d", "p"));
        ReflectionTestUtils.setField(busy, "id", 1L);

        quiet = new InteriorDesigner("Quiet", "quiet@example.test", "encoded", new Company("Quiet Studio", "d", "p"));
        ReflectionTestUtils.setField(quiet, "id", 2L);
    }

    @Test
    void ratesAreComputedAsAFractionOfBookingsNotRawCounts() {
        when(designerRepository.findAll()).thenReturn(List.of(busy));
        when(complaintRepository.countByDesignerId(1L)).thenReturn(3L);
        when(bookingRepository.countByDesignerId(1L)).thenReturn(10L);
        when(bookingRepository.countByDesignerIdAndStatus(1L, BookingStatus.CANCELLED)).thenReturn(2L);

        List<DesignerQualitySummary> result = service.designerQualitySummaries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).disputeRate()).isEqualTo(0.3);
        assertThat(result.get(0).cancellationRate()).isEqualTo(0.2);
    }

    @Test
    void designerWithNoBookingsHasNullRatesNotDivideByZero() {
        when(designerRepository.findAll()).thenReturn(List.of(quiet));
        when(complaintRepository.countByDesignerId(2L)).thenReturn(0L);
        when(bookingRepository.countByDesignerId(2L)).thenReturn(0L);
        when(bookingRepository.countByDesignerIdAndStatus(2L, BookingStatus.CANCELLED)).thenReturn(0L);

        List<DesignerQualitySummary> result = service.designerQualitySummaries();

        assertThat(result.get(0).disputeRate()).isNull();
        assertThat(result.get(0).cancellationRate()).isNull();
    }

    @Test
    void highestDisputeRateIsSortedFirstAndNullRatesGoLast() {
        when(designerRepository.findAll()).thenReturn(List.of(quiet, busy));
        when(complaintRepository.countByDesignerId(1L)).thenReturn(5L);
        when(bookingRepository.countByDesignerId(1L)).thenReturn(10L);
        when(bookingRepository.countByDesignerIdAndStatus(1L, BookingStatus.CANCELLED)).thenReturn(0L);
        when(complaintRepository.countByDesignerId(2L)).thenReturn(0L);
        when(bookingRepository.countByDesignerId(2L)).thenReturn(0L);
        when(bookingRepository.countByDesignerIdAndStatus(2L, BookingStatus.CANCELLED)).thenReturn(0L);

        List<DesignerQualitySummary> result = service.designerQualitySummaries();

        assertThat(result.get(0).designer()).isEqualTo(busy);
        assertThat(result.get(1).designer()).isEqualTo(quiet);
    }
}
