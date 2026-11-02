package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.web.dto.ShortlistTrendPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// IDR-10/11/12 - saving projects to favourites, and what designers can see about it
@Service
public class FavouriteService {

    private static final int TREND_DAYS = 30;

    private final ShortlistRepository shortlistRepository;
    private final ProjectRepository projectRepository;

    public FavouriteService(ShortlistRepository shortlistRepository, ProjectRepository projectRepository) {
        this.shortlistRepository = shortlistRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void add(Customer customer, Long projectId) {
        if (shortlistRepository.existsByCustomerIdAndProjectId(customer.getId(), projectId)) {
            return;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("That project couldn't be found."));
        shortlistRepository.save(new Shortlist(customer, project));
        project.incrementShortlistCount();
    }

    @Transactional
    public void remove(Customer customer, Long projectId) {
        shortlistRepository.findByCustomerIdAndProjectId(customer.getId(), projectId)
                .ifPresent(shortlist -> {
                    shortlistRepository.delete(shortlist);
                    shortlist.getProject().decrementShortlistCount();
                });
    }

    public List<Project> listForCustomer(Customer customer) {
        return shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(Shortlist::getProject)
                .toList();
    }

    public boolean isShortlisted(Customer customer, Long projectId) {
        return shortlistRepository.existsByCustomerIdAndProjectId(customer.getId(), projectId);
    }

    // IDR-11 - who shortlisted which of the designer's projects
    public List<Shortlist> shortlistersFor(InteriorDesigner designer) {
        return shortlistRepository.findByProjectDesignerIdOrderByCreatedAtDesc(designer.getId());
    }

    // IDR-12 - daily shortlist counts for the last 30 days, zero-filled so the chart isn't full of gaps
    public List<ShortlistTrendPoint> shortlistTrend(InteriorDesigner designer) {
        LocalDate since = LocalDate.now().minusDays(TREND_DAYS - 1L);
        Map<LocalDate, Long> countsByDay = shortlistRepository.findByProjectDesignerIdOrderByCreatedAtDesc(designer.getId())
                .stream()
                .map(shortlist -> shortlist.getCreatedAt().toLocalDate())
                .filter(date -> !date.isBefore(since))
                .collect(Collectors.groupingBy(date -> date, Collectors.counting()));

        List<ShortlistTrendPoint> trend = new ArrayList<>();
        for (long offset = 0; offset < TREND_DAYS; offset++) {
            LocalDate day = since.plusDays(offset);
            trend.add(new ShortlistTrendPoint(day, countsByDay.getOrDefault(day, 0L)));
        }
        return trend;
    }
}
