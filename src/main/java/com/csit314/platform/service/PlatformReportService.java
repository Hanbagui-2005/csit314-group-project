package com.csit314.platform.service;

import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.CategoryDesignerCoverage;
import com.csit314.platform.dto.PlatformReport;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public List<PlatformReport> reports() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                report("Today", now.minusDays(1)),
                report("This week", now.minusDays(7)),
                report("This month", now.minusDays(30))
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
                    return new CategoryDesignerCoverage(category.getId(), category.getName(), designers, demand,
                            demand > designers);
                })
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
