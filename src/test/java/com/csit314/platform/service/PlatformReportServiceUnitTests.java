package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.CategoryDesignerCoverage;
import com.csit314.platform.dto.PlatformReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// activity reports + PM-07
@ExtendWith(MockitoExtension.class)
class PlatformReportServiceUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock ProjectRepository projectRepository;
    @Mock BookingRepository bookingRepository;
    @Mock ComplaintRepository complaintRepository;
    @Mock CategoryRepository categoryRepository;
    PlatformReportService service;

    @BeforeEach
    void setUp() {
        service = new PlatformReportService(userAccountRepository, projectRepository, bookingRepository,
                complaintRepository, categoryRepository);
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

    @Test
    void flagsACategoryAsShortWhenDemandOutpacesActiveDesigners() {
        Category kitchens = new Category("Kitchens");
        ReflectionTestUtils.setField(kitchens, "id", 1L);
        Category bathrooms = new Category("Bathrooms");
        ReflectionTestUtils.setField(bathrooms, "id", 2L);
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(bathrooms, kitchens));

        when(projectRepository.countDistinctActiveDesignersByCategoryId(1L)).thenReturn(1L);
        when(bookingRepository.countByProjectCategoryId(1L)).thenReturn(5L);
        when(projectRepository.countDistinctActiveDesignersByCategoryId(2L)).thenReturn(5L);
        when(bookingRepository.countByProjectCategoryId(2L)).thenReturn(1L);

        List<CategoryDesignerCoverage> coverage = service.categoryDesignerCoverage();

        CategoryDesignerCoverage kitchenCoverage = coverage.stream()
                .filter(c -> c.categoryId().equals(1L)).findFirst().orElseThrow();
        assertThat(kitchenCoverage.shortage()).isTrue();

        CategoryDesignerCoverage bathroomCoverage = coverage.stream()
                .filter(c -> c.categoryId().equals(2L)).findFirst().orElseThrow();
        assertThat(bathroomCoverage.shortage()).isFalse();
    }

    // PM-07 - a single designer with 2 bookings isn't short-staffed just because
    // 2 (bookings) > 1 (designer); the ratio is what should matter
    @Test
    void aSingleDesignerWithLightBookingLoadIsNotFlaggedAsAShortage() {
        Category kitchens = new Category("Kitchens");
        ReflectionTestUtils.setField(kitchens, "id", 1L);
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(kitchens));
        when(projectRepository.countDistinctActiveDesignersByCategoryId(1L)).thenReturn(1L);
        when(bookingRepository.countByProjectCategoryId(1L)).thenReturn(2L);

        List<CategoryDesignerCoverage> coverage = service.categoryDesignerCoverage();

        assertThat(coverage.get(0).shortage()).isFalse();
    }

    // PM-06/PM-08 - "Today" means since midnight, "This week" since Monday, "This month"
    // since the 1st, not a rolling 24h/7d/30d window
    @Test
    void reportPeriodsUseCalendarBoundariesNotRollingWindows() {
        when(projectRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        service.reports();

        verify(projectRepository, times(3)).countByCreatedAtAfter(sinceCaptor.capture());
        LocalDate today = LocalDate.now();
        List<LocalDateTime> captured = sinceCaptor.getAllValues();
        assertThat(captured.get(0)).isEqualTo(today.atStartOfDay());
        assertThat(captured.get(1)).isEqualTo(today.with(DayOfWeek.MONDAY).atStartOfDay());
        assertThat(captured.get(2)).isEqualTo(today.withDayOfMonth(1).atStartOfDay());
    }
}
