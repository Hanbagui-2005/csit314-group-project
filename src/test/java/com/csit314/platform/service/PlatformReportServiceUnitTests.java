package com.csit314.platform.service;

import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.PlatformReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// PM-05
@ExtendWith(MockitoExtension.class)
class PlatformReportServiceUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock ProjectRepository projectRepository;
    @Mock BookingRepository bookingRepository;
    @Mock ComplaintRepository complaintRepository;
    PlatformReportService service;

    @BeforeEach
    void setUp() {
        service = new PlatformReportService(userAccountRepository, projectRepository, bookingRepository, complaintRepository);
    }

    @Test
    void producesOneReportForEachOfTheThreePeriods() {
        when(userAccountRepository.countByRoleAndCreatedAtAfter(any(), any(LocalDateTime.class))).thenReturn(1L);
        when(projectRepository.countByCreatedAtAfter(any())).thenReturn(2L);
        when(bookingRepository.countByCreatedAtAfter(any())).thenReturn(3L);
        when(complaintRepository.countByCreatedAtAfter(any())).thenReturn(4L);

        List<PlatformReport> reports = service.reports();

        assertThat(reports).extracting(PlatformReport::periodLabel)
                .containsExactly("Today", "This week", "This month");
        assertThat(reports.get(0).newProjects()).isEqualTo(2L);
        assertThat(reports.get(0).newBookings()).isEqualTo(3L);
        assertThat(reports.get(0).newComplaints()).isEqualTo(4L);
    }

    @Test
    void countsCustomersAndDesignersSeparatelyByRole() {
        when(userAccountRepository.countByRoleAndCreatedAtAfter(eq(Role.CUSTOMER), any())).thenReturn(5L);
        when(userAccountRepository.countByRoleAndCreatedAtAfter(eq(Role.INTERIOR_DESIGNER), any())).thenReturn(7L);

        PlatformReport today = service.reports().get(0);

        assertThat(today.newCustomers()).isEqualTo(5L);
        assertThat(today.newDesigners()).isEqualTo(7L);
    }
}
