package com.csit314.platform.service;

import com.csit314.platform.model.Role;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.ComplaintRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.PlatformReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// PM-05 - daily/weekly/monthly activity counts across the whole platform
@Service
public class PlatformReportService {

    private final UserAccountRepository userAccountRepository;
    private final ProjectRepository projectRepository;
    private final BookingRepository bookingRepository;
    private final ComplaintRepository complaintRepository;

    public PlatformReportService(UserAccountRepository userAccountRepository,
                                  ProjectRepository projectRepository,
                                  BookingRepository bookingRepository,
                                  ComplaintRepository complaintRepository) {
        this.userAccountRepository = userAccountRepository;
        this.projectRepository = projectRepository;
        this.bookingRepository = bookingRepository;
        this.complaintRepository = complaintRepository;
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

    private PlatformReport report(String periodLabel, LocalDateTime since) {
        long newCustomers = userAccountRepository.countByRoleAndCreatedAtAfter(Role.CUSTOMER, since);
        long newDesigners = userAccountRepository.countByRoleAndCreatedAtAfter(Role.INTERIOR_DESIGNER, since);
        long newProjects = projectRepository.countByCreatedAtAfter(since);
        long newBookings = bookingRepository.countByCreatedAtAfter(since);
        long newComplaints = complaintRepository.countByCreatedAtAfter(since);
        return new PlatformReport(periodLabel, newCustomers, newDesigners, newProjects, newBookings, newComplaints);
    }
}
