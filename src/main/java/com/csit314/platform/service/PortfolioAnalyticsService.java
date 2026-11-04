package com.csit314.platform.service;

import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.dto.CategoryBreakdown;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// IDR-03 - which categories in a designer's portfolio get the most engagement (views +
// shortlists), so they know where to focus future projects, not just which categories
// happen to have the most projects in them. Project.viewCount/shortlistCount are real,
// live-tracked numbers now (IDR-10's search/favourites work), so this ranks on them
// directly instead of a placeholder project-count sort.
//
// "Styles" from the story's wording aren't modeled separately anywhere in the platform -
// Category is the only taxonomy dimension that exists (see CategoryManagementService) -
// so this covers the category half of the story. A real style dimension would need its
// own field/taxonomy, which is a bigger addition than this fix.
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

        Map<String, List<Project>> projectsByCategory = projects.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getName()));

        int total = projects.size();
        return projectsByCategory.entrySet().stream()
                .map(entry -> {
                    List<Project> categoryProjects = entry.getValue();
                    double averageViews = categoryProjects.stream()
                            .mapToInt(Project::getViewCount).average().orElse(0);
                    double averageShortlists = categoryProjects.stream()
                            .mapToInt(Project::getShortlistCount).average().orElse(0);
                    return new CategoryBreakdown(
                            entry.getKey(),
                            categoryProjects.size(),
                            Math.round(categoryProjects.size() * 100.0 / total),
                            averageViews,
                            averageShortlists);
                })
                // highest engagement first - views and shortlists combined into one
                // ranking signal rather than picking just one of them
                .sorted(Comparator.comparing(
                        (CategoryBreakdown breakdown) -> breakdown.averageViews() + breakdown.averageShortlists())
                        .reversed())
                .toList();
    }
}
