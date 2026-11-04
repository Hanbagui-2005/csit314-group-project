package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.AnomalyAlert;
import com.csit314.platform.dto.CategoryDesignerCoverage;
import com.csit314.platform.dto.CategoryViewRanking;
import com.csit314.platform.dto.GrowthForecast;
import com.csit314.platform.dto.TopDesigner;
import com.csit314.platform.dto.TopProject;
import com.csit314.platform.dto.PlatformReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any())).thenReturn(1L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(2L);
        when(bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(3L);
        when(complaintRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(4L);

        List<PlatformReport> reports = service.reports();

        assertThat(reports).extracting(PlatformReport::periodLabel)
                .containsExactly("Today", "This week", "This month");
        assertThat(reports.get(0).newProjects()).isEqualTo(2L);
        assertThat(reports.get(0).newBookings()).isEqualTo(3L);
        assertThat(reports.get(0).newComplaints()).isEqualTo(4L);
    }

    @Test
    void countsCustomersAndDesignersSeparatelyByRole() {
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(Role.CUSTOMER), any(), any())).thenReturn(5L);
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(Role.INTERIOR_DESIGNER), any(), any())).thenReturn(7L);

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
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(bathrooms, kitchens));

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
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(kitchens));
        when(projectRepository.countDistinctActiveDesignersByCategoryId(1L)).thenReturn(1L);
        when(bookingRepository.countByProjectCategoryId(1L)).thenReturn(2L);

        List<CategoryDesignerCoverage> coverage = service.categoryDesignerCoverage();

        assertThat(coverage.get(0).shortage()).isFalse();
    }

    // PM-06/PM-08 - "Today" means since midnight, "This week" since Monday, "This month"
    // since the 1st, not a rolling 24h/7d/30d window
    @Test
    void reportPeriodsUseCalendarBoundariesNotRollingWindows() {
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        service.reports();

        verify(projectRepository, times(3))
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startCaptor.capture(), any());
        LocalDate today = LocalDate.now();
        List<LocalDateTime> captured = startCaptor.getAllValues();
        assertThat(captured.get(0)).isEqualTo(today.atStartOfDay());
        assertThat(captured.get(1)).isEqualTo(today.with(DayOfWeek.MONDAY).atStartOfDay());
        assertThat(captured.get(2)).isEqualTo(today.withDayOfMonth(1).atStartOfDay());
    }

    @Test
    void ranksCategoriesWithMostViewsFirst() {
        Category kitchens = new Category("Kitchens");
        ReflectionTestUtils.setField(kitchens, "id", 1L);
        Category bathrooms = new Category("Bathrooms");
        ReflectionTestUtils.setField(bathrooms, "id", 2L);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(bathrooms, kitchens));

        when(projectRepository.sumViewCountByCategoryId(1L)).thenReturn(50L);
        when(projectRepository.sumViewCountByCategoryId(2L)).thenReturn(120L);

        List<CategoryViewRanking> ranking = service.categoryViewRanking();

        assertThat(ranking).extracting(CategoryViewRanking::categoryName)
                .containsExactly("Bathrooms", "Kitchens");
        assertThat(ranking.get(0).totalViews()).isEqualTo(120L);
    }

    // PM-10
    @Test
    void topDesignersDelegatesToTheRepositoryWithAFixedLimit() {
        TopDesigner designer = new TopDesigner(1L, "Studio", 100L, 20L);
        when(projectRepository.topDesigners(any(Pageable.class))).thenReturn(List.of(designer));

        assertThat(service.topDesigners()).containsExactly(designer);
    }

    @Test
    void topProjectsDelegatesToTheRepositoryWithAFixedLimit() {
        TopProject project = new TopProject(1L, "Kitchen remodel", "Studio", 100, 20);
        when(projectRepository.topProjects(any(Pageable.class))).thenReturn(List.of(project));

        assertThat(service.topProjects()).containsExactly(project);
    }

    // PM-02
    @Test
    void growthForecastAveragesTheLastThreeCompletedMonths() {
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(10L, 20L, 30L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(4L, 6L, 8L);

        GrowthForecast forecast = service.growthForecast();

        assertThat(forecast.history()).hasSize(3);
        assertThat(forecast.projectedNewUsers()).isEqualTo(20L);
        assertThat(forecast.projectedNewListings()).isEqualTo(6L);
    }

    // PM-14 - checks yesterday (a complete day), not today, against the 7 complete
    // days before that - see PlatformReportService's own notes on why
    @Test
    void anomalyAlertsFlagsYesterdayMuchHigherThanTheRecentAverage() {
        // the 7 days before yesterday average to 2 new sign-ups/day, yesterday had 10 (a 5x spike)
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(10L, 2L, 2L, 2L, 2L, 2L, 2L, 2L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        List<AnomalyAlert> alerts = service.anomalyAlerts();

        assertThat(alerts).extracting(AnomalyAlert::metricLabel).containsExactly("New sign-ups");
        assertThat(alerts.get(0).value()).isEqualTo(10L);
    }

    @Test
    void anomalyAlertsFlagsYesterdayMuchLowerThanTheRecentAverage() {
        // the 7 days before yesterday average to 10 new sign-ups/day, yesterday had 0 (a total drop)
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(0L, 10L, 10L, 10L, 10L, 10L, 10L, 10L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        List<AnomalyAlert> alerts = service.anomalyAlerts();

        assertThat(alerts).extracting(AnomalyAlert::metricLabel).containsExactly("New sign-ups");
    }

    // the exact bug Anthony flagged: today's partial count compared against a
    // full-day baseline used to look like a drop on every single call, regardless of
    // the actual (unstubbed, so defaults to 0) activity - now it's never consulted at all
    @Test
    void anomalyAlertsNeverLooksAtTodayAtAll() {
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(5L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(5L);

        service.anomalyAlerts();

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userAccountRepository, atLeastOnce())
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(startCaptor.capture(), any());
        LocalDateTime today = LocalDate.now().atStartOfDay();
        assertThat(startCaptor.getAllValues()).doesNotContain(today);
    }

    @Test
    void anomalyAlertsStaysQuietWhenNothingIsUnusual() {
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(3L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(1L);

        assertThat(service.anomalyAlerts()).isEmpty();
    }

    @Test
    void anomalyAlertsIgnoresASmallBaselineDroppingToZero() {
        // a baseline under 2/day dropping to zero isn't "significant" - it's noise
        when(userAccountRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(0L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);

        assertThat(service.anomalyAlerts()).isEmpty();
    }

    // PM-09
    @Test
    void exportReportsAsCsvIncludesAHeaderAndOneRowPerPeriod() {
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any())).thenReturn(1L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(2L);
        when(bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(3L);
        when(complaintRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(4L);

        String csv = service.exportReportsAsCsv();

        assertThat(csv).startsWith("Period,New Customers,New Designers,New Projects,New Bookings,New Complaints\n");
        assertThat(csv).contains("Today,1,1,2,3,4\n");
        assertThat(csv.lines().count()).isEqualTo(4); // header + 3 periods
    }

    // PM-12 - a complete (midnight-to-midnight) day, not "since midnight today" like
    // exportReportsAsCsv()'s "Today" row - that would make the scheduled job's 6am
    // output nearly empty
    @Test
    void exportReportAsCsvForCoversExactlyTheGivenDay() {
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any())).thenReturn(2L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(3L);
        when(bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(4L);
        when(complaintRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(5L);

        String csv = service.exportReportAsCsvFor(LocalDate.of(2026, 11, 3));

        assertThat(csv).isEqualTo("Period,New Customers,New Designers,New Projects,New Bookings,New Complaints\n"
                + "Yesterday,2,2,3,4,5\n");

        verify(projectRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDate.of(2026, 11, 3).atStartOfDay(), LocalDate.of(2026, 11, 4).atStartOfDay());
    }

    // Anthony's review - this used to always build the CSV from "yesterday relative to
    // right now" no matter what date was passed in, so a backfill call for an older
    // date silently produced today's-minus-one numbers in a file named after that
    // older date instead
    @Test
    void exportReportAsCsvForUsesTheGivenDateNotTodayMinusOne() {
        when(userAccountRepository.countByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(), any(), any())).thenReturn(0L);
        when(projectRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);
        when(complaintRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any())).thenReturn(0L);

        LocalDate someOlderDate = LocalDate.now().minusDays(10);
        service.exportReportAsCsvFor(someOlderDate);

        verify(projectRepository).countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                someOlderDate.atStartOfDay(), someOlderDate.plusDays(1).atStartOfDay());
    }
}
