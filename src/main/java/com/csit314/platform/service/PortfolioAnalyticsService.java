package com.csit314.platform.service;

import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.CategoryBreakdown;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// IDR-03 - how much of a designer's portfolio falls into each category
//
// NOTE for whoever builds real view/shortlist tracking (Customer search + favourites,
// still mock data in PageController as of this commit): viewCount/shortlistCount on
// Project are never incremented anywhere yet, so this only breaks down by category for
// now. Once views/shortlists are real, this is the place to add an "engagement" ranking
// (e.g. average views per category) alongside the project-count breakdown below.
@Service
public class PortfolioAnalyticsService {

    private final ProjectRepository projectRepository;

    public PortfolioAnalyticsService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<CategoryBreakdown> categoryBreakdown(Long designerId) {
        List<Project> projects = projectRepository.findByDesignerIdOrderByCreatedAtDesc(designerId);
        if (projects.isEmpty()) {
            return List.of();
        }

        Map<String, Long> countByCategory = projects.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getName(), Collectors.counting()));

        int total = projects.size();
        return countByCategory.entrySet().stream()
                .map(entry -> new CategoryBreakdown(
                        entry.getKey(),
                        entry.getValue().intValue(),
                        Math.round(entry.getValue() * 100.0 / total)))
                .sorted(Comparator.comparing(CategoryBreakdown::projectCount).reversed())
                .toList();
    }
}
