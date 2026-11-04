package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// IDR-03 - category breakdown ranked by engagement (views + shortlists), not just how
// many projects happen to be in each category
@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    PortfolioAnalyticsService service;

    InteriorDesigner designer;
    Category kitchen;
    Category bathroom;

    @BeforeEach
    void setUp() {
        service = new PortfolioAnalyticsService(projectRepository);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 1L);

        kitchen = new Category("Kitchen");
        bathroom = new Category("Bathroom");
    }

    private Project projectWithEngagement(Category category, int views, int shortlists) {
        Project project = new Project("Title", "x", category, designer);
        for (int i = 0; i < views; i++) {
            project.incrementViewCount();
        }
        ReflectionTestUtils.setField(project, "shortlistCount", shortlists);
        return project;
    }

    @Test
    void emptyPortfolioProducesNoBreakdown() {
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertThat(service.categoryBreakdown(1L)).isEmpty();
    }

    @Test
    void countsAndPercentagesAreGroupedByCategory() {
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                projectWithEngagement(kitchen, 0, 0),
                projectWithEngagement(kitchen, 0, 0),
                projectWithEngagement(bathroom, 0, 0)));

        List<com.csit314.platform.dto.CategoryBreakdown> result = service.categoryBreakdown(1L);

        var kitchenRow = result.stream().filter(r -> r.categoryName().equals("Kitchen")).findFirst().orElseThrow();
        assertThat(kitchenRow.projectCount()).isEqualTo(2);
        assertThat(kitchenRow.percentage()).isEqualTo(67);
    }

    @Test
    void averageViewsAndShortlistsAreComputedPerCategory() {
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                projectWithEngagement(kitchen, 10, 2),
                projectWithEngagement(kitchen, 20, 4)));

        var row = service.categoryBreakdown(1L).get(0);

        assertThat(row.averageViews()).isEqualTo(15.0);
        assertThat(row.averageShortlists()).isEqualTo(3.0);
    }

    // IDR-03 - the actual ask: rank by engagement, not by which category just
    // happens to have more projects in it
    @Test
    void higherEngagementCategoryIsRankedFirstEvenWithFewerProjects() {
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                projectWithEngagement(kitchen, 1, 0),
                projectWithEngagement(kitchen, 1, 0),
                projectWithEngagement(kitchen, 1, 0),
                projectWithEngagement(bathroom, 50, 10)));

        List<com.csit314.platform.dto.CategoryBreakdown> result = service.categoryBreakdown(1L);

        assertThat(result.get(0).categoryName()).isEqualTo("Bathroom");
        assertThat(result.get(1).categoryName()).isEqualTo("Kitchen");
    }
}
