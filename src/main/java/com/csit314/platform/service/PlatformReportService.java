package com.csit314.platform.service;

import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.CategoryDesignerCoverage;
import com.csit314.platform.dto.CategoryViewRanking;
import com.csit314.platform.dto.PlatformReport;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
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

    // PM-10 - categories ranked by total views across their projects, so PM can see what's popular
    @Transactional(readOnly = true)
    public List<CategoryViewRanking> categoryViewRanking() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(category -> new CategoryViewRanking(category.getId(), category.getName(),
                        projectRepository.sumViewCountByCategoryId(category.getId())))
                .sorted(Comparator.comparingLong(CategoryViewRanking::totalViews).reversed())
                .toList();
    }

    private PlatformReport report(String periodLabel, LocalDateTime since) {
        long newCustomers = userAccountRepository.countByRoleAndCreatedAtAfter(Role.CUSTOMER, since);
        long newDesigners = userAccountRepository.countByRoleAndCreatedAtAfter(Role.INTERIOR_DESIGNER, since);
        long newProjects = projectRepository.countByCreatedAtAfter(since);
        long newBookings = bookingRepository.countByCreatedAtAfter(since);
        long newComplaints = complaintRepository.countByCreatedAtAfter(since);
        return new PlatformReport(periodLabel, newCustomers, newDesigners, newProjects, newBookings, newComplaints);
    }
}
