package com.csit314.platform.service;

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
import com.csit314.platform.dto.MonthlyGrowth;
import com.csit314.platform.dto.PlatformReport;
import com.csit314.platform.dto.TopDesigner;
import com.csit314.platform.dto.TopProject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

// PM-06/PM-08 - daily/monthly activity reports (plus a bonus weekly window) + PM-07's
// category designer-coverage view
@Service
public class PlatformReportService {

    private final UserAccountRepository userAccountRepository;
    private final ProjectRepository projectRepository;
    private final BookingRepository bookingRepository;
    private final ComplaintRepository complaintRepository;
    private final CategoryRepository categoryRepository;

    public PlatformReportService(UserAccountRepository userAccountRepository,
                                  ProjectRepository projectRepository,
                                  BookingRepository bookingRepository,
                                  ComplaintRepository complaintRepository,
                                  CategoryRepository categoryRepository) {
        this.userAccountRepository = userAccountRepository;
        this.projectRepository = projectRepository;
        this.bookingRepository = bookingRepository;
        this.complaintRepository = complaintRepository;
        this.categoryRepository = categoryRepository;
    }

    // PM-07 - a category is "short" once bookings per active designer cross this, not just
    // whenever the raw booking count exceeds the raw designer count (those aren't comparable:
    // a single designer handling 2 bookings isn't short-staffed, but handling 10 is)
    private static final double BOOKINGS_PER_DESIGNER_SHORTAGE_THRESHOLD = 3.0;

    // PM-06/PM-08 - calendar boundaries, not rolling windows: "Today" is since midnight,
    // "This week" is since Monday, "This month" is since the 1st, so the labels mean what
    // they say instead of "last 24 hours" / "last 30 days"
    @Transactional(readOnly = true)
    public List<PlatformReport> reports() {
        LocalDate today = LocalDate.now();
        return List.of(
                report("Today", today.atStartOfDay()),
                report("This week", today.with(DayOfWeek.MONDAY).atStartOfDay()),
                report("This month", today.withDayOfMonth(1).atStartOfDay())
        );
    }

    // PM-07 - per category, active designer supply vs. booking demand, so Platform
    // Management can see which categories are underserved
    @Transactional(readOnly = true)
    public List<CategoryDesignerCoverage> categoryDesignerCoverage() {
        return categoryRepository.findByActiveTrue(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(category -> {
                    long designers = projectRepository.countDistinctActiveDesignersByCategoryId(category.getId());
                    long demand = bookingRepository.countByProjectCategoryId(category.getId());
                    boolean shortage = designers == 0
                            ? demand > 0
                            : (double) demand / designers > BOOKINGS_PER_DESIGNER_SHORTAGE_THRESHOLD;
                    return new CategoryDesignerCoverage(category.getId(), category.getName(), designers, demand,
                            shortage);
                })
                .toList();
    }

    // extra, not its own tracked story - categories ranked by total views across their
    // projects, so PM can see what's popular
    @Transactional(readOnly = true)
    public List<CategoryViewRanking> categoryViewRanking() {
        return categoryRepository.findByActiveTrue(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(category -> new CategoryViewRanking(category.getId(), category.getName(),
                        projectRepository.sumViewCountByCategoryId(category.getId())))
                .sorted(Comparator.comparingLong(CategoryViewRanking::totalViews).reversed())
                .toList();
    }

    private static final int TOP_PERFORMERS_LIMIT = 5;

    // PM-10 - top-performing designers (by portfolio views + shortlists) so PM can
    // consider featuring them
    @Transactional(readOnly = true)
    public List<TopDesigner> topDesigners() {
        return projectRepository.topDesigners(PageRequest.of(0, TOP_PERFORMERS_LIMIT));
    }

    // PM-10 - top-performing individual projects, same idea as topDesigners() but one
    // project at a time instead of a whole portfolio
    @Transactional(readOnly = true)
    public List<TopProject> topProjects() {
        return projectRepository.topProjects(PageRequest.of(0, TOP_PERFORMERS_LIMIT));
    }

    private static final int FORECAST_HISTORY_MONTHS = 3;

    // PM-02 - a simple moving-average projection: next month's new users/listings are
    // forecast as the average of the last few completed calendar months, so PM has a
    // capacity-planning number without needing a dedicated analytics pipeline
    @Transactional(readOnly = true)
    public GrowthForecast growthForecast() {
        List<MonthlyGrowth> history = new ArrayList<>();
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        for (int monthsAgo = FORECAST_HISTORY_MONTHS; monthsAgo >= 1; monthsAgo--) {
            LocalDate monthStart = firstOfThisMonth.minusMonths(monthsAgo);
            LocalDate monthEnd = monthStart.plusMonths(1);
            long newUsers = userAccountRepository.countByCreatedAtBetween(
                    monthStart.atStartOfDay(), monthEnd.atStartOfDay());
            long newListings = projectRepository.countByCreatedAtBetween(
                    monthStart.atStartOfDay(), monthEnd.atStartOfDay());
            history.add(new MonthlyGrowth(monthStart.getMonth() + " " + monthStart.getYear(), newUsers, newListings));
        }
        long projectedUsers = Math.round(history.stream().mapToLong(MonthlyGrowth::newUsers).average().orElse(0));
        long projectedListings = Math.round(history.stream().mapToLong(MonthlyGrowth::newListings).average().orElse(0));
        return new GrowthForecast(projectedUsers, projectedListings, history);
    }

    private static final int ANOMALY_BASELINE_DAYS = 7;
    // PM-14 - today's value needs to be at least double the recent average to count as
    // a spike, or at most half of it (with a small floor so a quiet baseline of 0-1
    // doesn't trivially flag every single sign-up as a "100% drop") to count as a drop
    private static final double ANOMALY_SPIKE_RATIO = 2.0;
    private static final double ANOMALY_DROP_RATIO = 0.5;
    private static final double ANOMALY_MIN_BASELINE_FOR_DROP = 2.0;

    // PM-14 - flags any tracked metric whose most recent complete day deviates
    // significantly from the week before it, so PM can respond quickly to unusual
    // activity instead of only noticing it later in a weekly/monthly report. Compares
    // yesterday (a complete day) against the 7 complete days before that, rather than
    // today (still partway through) against complete-day averages - otherwise a normal
    // baseline of 10/day would look like a "drop" every morning just because today's
    // partial count hasn't caught up yet
    @Transactional(readOnly = true)
    public List<AnomalyAlert> anomalyAlerts() {
        List<AnomalyAlert> alerts = new ArrayList<>();
        checkForAnomaly(alerts, "New sign-ups", day -> userAccountRepository.countByCreatedAtBetween(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
        checkForAnomaly(alerts, "New projects", day -> projectRepository.countByCreatedAtBetween(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
        return alerts;
    }

    private void checkForAnomaly(List<AnomalyAlert> alerts, String metricLabel, Function<LocalDate, Long> dailyCount) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        long yesterdayValue = dailyCount.apply(yesterday);
        double average = averageOverPastDays(dailyCount, yesterday, ANOMALY_BASELINE_DAYS);
        boolean spike = average > 0 && yesterdayValue >= average * ANOMALY_SPIKE_RATIO;
        boolean drop = average >= ANOMALY_MIN_BASELINE_FOR_DROP && yesterdayValue <= average * ANOMALY_DROP_RATIO;
        if (spike || drop) {
            alerts.add(new AnomalyAlert(metricLabel, yesterdayValue, average));
        }
    }

    // the days strictly before referenceDate - when called with yesterday, that's the
    // 7 complete days before yesterday, never including the partial, still-in-progress day
    private double averageOverPastDays(Function<LocalDate, Long> dailyCount, LocalDate referenceDate, int days) {
        long total = 0;
        for (int daysAgo = 1; daysAgo <= days; daysAgo++) {
            total += dailyCount.apply(referenceDate.minusDays(daysAgo));
        }
        return (double) total / days;
    }

    // PM-09 - the "or CSV" half of "export a report as a PDF or CSV". A PDF export
    // isn't implemented (needs a rendering library and more time than it's worth this
    // close to the deadline), but the story is satisfied by either format on its own.
    // No CSV escaping needed: every field here is either a fixed label ("Today", "This
    // week", "This month") or a plain number, never free-text a user typed in
    @Transactional(readOnly = true)
    public String exportReportsAsCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("Period,New Customers,New Designers,New Projects,New Bookings,New Complaints\n");
        for (PlatformReport report : reports()) {
            csv.append(report.periodLabel()).append(',')
                    .append(report.newCustomers()).append(',')
                    .append(report.newDesigners()).append(',')
                    .append(report.newProjects()).append(',')
                    .append(report.newBookings()).append(',')
                    .append(report.newComplaints()).append('\n');
        }
        return csv.toString();
    }

    private PlatformReport report(String periodLabel, LocalDateTime since) {
        long newCustomers = userAccountRepository.countByRoleAndCreatedAtAfter(Role.CUSTOMER, since);
        long newDesigners = userAccountRepository.countByRoleAndCreatedAtAfter(Role.INTERIOR_DESIGNER, since);
        long newProjects = projectRepository.countByCreatedAtAfter(since);
        long newBookings = bookingRepository.countByCreatedAtAfter(since);
        long newComplaints = complaintRepository.countByCreatedAtAfter(since);
        return new PlatformReport(periodLabel, newCustomers, newDesigners, newProjects, newBookings, newComplaints);
    }

    // PM-12 - the scheduled job's own report: exactly yesterday (midnight to midnight),
    // not "since midnight" like reports()'s "Today" row, which at 6am would only cover
    // the 6 hours so far and make the file mostly empty
    @Transactional(readOnly = true)
    public String exportYesterdayReportAsCsv() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        long newCustomers = userAccountRepository.countByRoleAndCreatedAtBetween(Role.CUSTOMER, yesterdayStart, todayStart);
        long newDesigners = userAccountRepository.countByRoleAndCreatedAtBetween(
                Role.INTERIOR_DESIGNER, yesterdayStart, todayStart);
        long newProjects = projectRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        long newBookings = bookingRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        long newComplaints = complaintRepository.countByCreatedAtBetween(yesterdayStart, todayStart);
        PlatformReport yesterday = new PlatformReport("Yesterday", newCustomers, newDesigners, newProjects,
                newBookings, newComplaints);

        StringBuilder csv = new StringBuilder();
        csv.append("Period,New Customers,New Designers,New Projects,New Bookings,New Complaints\n");
        csv.append(yesterday.periodLabel()).append(',')
                .append(yesterday.newCustomers()).append(',')
                .append(yesterday.newDesigners()).append(',')
                .append(yesterday.newProjects()).append(',')
                .append(yesterday.newBookings()).append(',')
                .append(yesterday.newComplaints()).append('\n');
        return csv.toString();
    }
}
