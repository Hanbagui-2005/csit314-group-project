package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.web.dto.DesignerComparison;
import com.csit314.platform.web.dto.ShortlistTrendPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

// IDR-10/11/12 - saving projects to favourites, and what designers can see about it
@Service
public class FavouriteService {

    private static final int TREND_DAYS = 30;
    private static final int MAX_RECOMMENDATIONS = 6;

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

    // CUS-05 - other projects in categories the customer has already shortlisted into.
    // "search history" isn't tracked anywhere in this app, so this is purely favourites-based
    public List<Project> recommendationsFor(Customer customer) {
        List<Project> shortlisted = listForCustomer(customer);
        if (shortlisted.isEmpty()) {
            return List.of();
        }
        List<Long> categoryIds = shortlisted.stream().map(p -> p.getCategory().getId()).distinct().toList();
        List<Long> excludeIds = shortlisted.stream().map(Project::getId).toList();
        return projectRepository.findByCategoryIdInAndIdNotInOrderByCreatedAtDesc(categoryIds, excludeIds).stream()
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    // CUS-06 - one row per distinct designer behind the customer's current shortlist.
    // Price range/styles come from that designer's whole portfolio, not just the
    // shortlisted project, since that's more representative of what hiring them looks like
    public List<DesignerComparison> compareShortlistedDesigners(Customer customer) {
        Map<Long, InteriorDesigner> designersById = new LinkedHashMap<>();
        for (Project project : listForCustomer(customer)) {
            designersById.putIfAbsent(project.getDesigner().getId(), project.getDesigner());
        }
        return designersById.values().stream().map(this::toComparison).toList();
    }

    private DesignerComparison toComparison(InteriorDesigner designer) {
        List<Project> portfolio = projectRepository.findByDesignerIdOrderByCreatedAtDesc(designer.getId());
        List<Integer> mins = portfolio.stream().map(Project::getBudgetMin).filter(Objects::nonNull).toList();
        List<Integer> maxes = portfolio.stream().map(Project::getBudgetMax).filter(Objects::nonNull).toList();
        Integer priceMin = mins.isEmpty() ? null : Collections.min(mins);
        Integer priceMax = maxes.isEmpty() ? null : Collections.max(maxes);
        List<String> styles = portfolio.stream().map(p -> p.getCategory().getName()).distinct().toList();
        return new DesignerComparison(designer, priceMin, priceMax, styles);
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
