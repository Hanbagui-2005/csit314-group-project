package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.web.dto.DesignerComparison;
import com.csit314.platform.web.dto.ShortlistTrendPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #37 was CUS-12 (budget estimate, skipped) - these are #22/#23/#24, IDR-10/11/12. */
@ExtendWith(MockitoExtension.class)
class FavouriteServiceUnitTests {

    @Mock ShortlistRepository shortlistRepository;
    @Mock ProjectRepository projectRepository;
    FavouriteService service;

    Customer customer;
    InteriorDesigner designer;
    Project project;

    @BeforeEach
    void setUp() {
        service = new FavouriteService(shortlistRepository, projectRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);

        Category category = new Category("Kitchen");
        ReflectionTestUtils.setField(category, "id", 10L);

        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void addingSavesAShortlistAndBumpsTheCount() {
        when(shortlistRepository.existsByCustomerIdAndProjectId(1L, 100L)).thenReturn(false);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.add(customer, 100L);

        verify(shortlistRepository).save(any(Shortlist.class));
        assertThat(project.getShortlistCount()).isEqualTo(1);
    }

    @Test
    void addingTwiceIsANoOp() {
        when(shortlistRepository.existsByCustomerIdAndProjectId(1L, 100L)).thenReturn(true);

        service.add(customer, 100L);

        verify(shortlistRepository, never()).save(any());
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void removingDeletesTheShortlistAndDropsTheCount() {
        project.incrementShortlistCount();
        Shortlist shortlist = new Shortlist(customer, project);
        when(shortlistRepository.findByCustomerIdAndProjectId(1L, 100L)).thenReturn(Optional.of(shortlist));

        service.remove(customer, 100L);

        verify(shortlistRepository).delete(shortlist);
        assertThat(project.getShortlistCount()).isZero();
    }

    @Test
    void removingSomethingNeverSavedIsANoOp() {
        when(shortlistRepository.findByCustomerIdAndProjectId(1L, 100L)).thenReturn(Optional.empty());

        service.remove(customer, 100L);

        verify(shortlistRepository, never()).delete(any(Shortlist.class));
    }

    @Test
    void trendZeroFillsDaysWithNoActivity() {
        when(shortlistRepository.findByProjectDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        List<ShortlistTrendPoint> trend = service.shortlistTrend(designer);

        assertThat(trend).hasSize(30);
        assertThat(trend).allMatch(point -> point.count() == 0);
    }

    @Test
    void trendCountsShortlistsOnTheRightDay() {
        Shortlist today = new Shortlist(customer, project);
        ReflectionTestUtils.setField(today, "createdAt", LocalDateTime.now());
        when(shortlistRepository.findByProjectDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(today));

        List<ShortlistTrendPoint> trend = service.shortlistTrend(designer);

        long todayCount = trend.get(trend.size() - 1).count();
        assertThat(todayCount).isEqualTo(1);
    }

    // CUS-05
    @Test
    void recommendationsComeFromCategoriesTheCustomerAlreadyLikes() {
        Shortlist shortlist = new Shortlist(customer, project);
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(shortlist));
        Project other = new Project("Another kitchen", "x", project.getCategory(), designer);
        ReflectionTestUtils.setField(other, "id", 200L);
        when(projectRepository.findByCategoryIdInAndIdNotInOrderByCreatedAtDesc(List.of(10L), List.of(100L)))
                .thenReturn(List.of(other));

        List<Project> recommendations = service.recommendationsFor(customer);

        assertThat(recommendations).containsExactly(other);
    }

    @Test
    void noRecommendationsWithNothingShortlistedYet() {
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertThat(service.recommendationsFor(customer)).isEmpty();
        verifyNoInteractions(projectRepository);
    }

    // CUS-06
    @Test
    void comparisonGivesOneRowPerDistinctDesignerWithPriceRangeAndStyles() {
        Shortlist shortlist = new Shortlist(customer, project);
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(shortlist));

        Project cheap = new Project("Budget kitchen", "x", project.getCategory(), designer);
        ReflectionTestUtils.setField(cheap, "budgetMin", 2000);
        ReflectionTestUtils.setField(cheap, "budgetMax", 4000);
        Category bathroom = new Category("Bathroom");
        ReflectionTestUtils.setField(bathroom, "id", 11L);
        Project premium = new Project("Premium bathroom", "x", bathroom, designer);
        ReflectionTestUtils.setField(premium, "budgetMin", 8000);
        ReflectionTestUtils.setField(premium, "budgetMax", 15000);
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(cheap, premium));

        List<DesignerComparison> comparisons = service.compareShortlistedDesigners(customer);

        assertThat(comparisons).hasSize(1);
        DesignerComparison row = comparisons.get(0);
        assertThat(row.priceMin()).isEqualTo(2000);
        assertThat(row.priceMax()).isEqualTo(15000);
        assertThat(row.styles()).containsExactlyInAnyOrder("Kitchen", "Bathroom");
    }

    @Test
    void comparisonDedupesByDesignerAcrossMultipleShortlistedProjects() {
        Project secondProject = new Project("Another kitchen", "x", project.getCategory(), designer);
        ReflectionTestUtils.setField(secondProject, "id", 200L);
        Shortlist first = new Shortlist(customer, project);
        Shortlist second = new Shortlist(customer, secondProject);
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(first, second));
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(project, secondProject));

        assertThat(service.compareShortlistedDesigners(customer)).hasSize(1);
    }
}
