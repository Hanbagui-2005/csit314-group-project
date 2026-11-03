package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.dto.DesignerComparison;
import com.csit314.platform.dto.RatingSummary;
import com.csit314.platform.dto.ShortlistTrendPoint;
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
    @Mock RatingService ratingService;
    @Mock ShortlistWriter shortlistWriter;
    FavouriteService service;

    Customer customer;
    InteriorDesigner designer;
    Project project;

    @BeforeEach
    void setUp() {
        service = new FavouriteService(shortlistRepository, projectRepository, ratingService, shortlistWriter);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
        designer.setStatus(AccountStatus.ACTIVE);

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

        verify(shortlistWriter).insert(customer, project);
        assertThat(project.getShortlistCount()).isEqualTo(1);
    }

    @Test
    void addingTwiceIsANoOp() {
        when(shortlistRepository.existsByCustomerIdAndProjectId(1L, 100L)).thenReturn(true);

        service.add(customer, 100L);

        verifyNoInteractions(shortlistWriter);
        verify(projectRepository, never()).findById(any());
    }

    // the exists-check-then-insert isn't atomic, so a double-click can slip past the
    // exists() check twice before either insert commits; ShortlistWriter's own (separate)
    // transaction rolls back cleanly on the constraint violation and FavouriteService
    // catches it here, instead of a raw 500 error reaching the customer
    @Test
    void losingTheRaceToSaveTheSameShortlistTwiceIsHandledGracefully() {
        when(shortlistRepository.existsByCustomerIdAndProjectId(1L, 100L)).thenReturn(false);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                .when(shortlistWriter).insert(customer, project);

        service.add(customer, 100L);

        assertThat(project.getShortlistCount()).isZero();
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

    // a customer shouldn't see a suspended/pending/rejected designer's project on their
    // favourites list just because they saved it before the designer's status changed -
    // same visibility rule as search and the project detail page
    @Test
    void favouritesListHidesProjectsFromDesignersWhoArentActive() {
        designer.setStatus(AccountStatus.SUSPENDED);
        Shortlist shortlist = new Shortlist(customer, project);
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(shortlist));

        assertThat(service.listForCustomer(customer)).isEmpty();
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

    // a suspended/pending/rejected designer's project shouldn't surface as a recommendation
    @Test
    void recommendationsExcludeProjectsFromDesignersWhoArentActive() {
        Shortlist shortlist = new Shortlist(customer, project);
        when(shortlistRepository.findByCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(shortlist));
        InteriorDesigner suspended = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(suspended, "id", 3L);
        suspended.setStatus(AccountStatus.SUSPENDED);
        Project fromSuspendedDesigner = new Project("Suspended designer's project", "x", project.getCategory(), suspended);
        ReflectionTestUtils.setField(fromSuspendedDesigner, "id", 200L);
        when(projectRepository.findByCategoryIdInAndIdNotInOrderByCreatedAtDesc(List.of(10L), List.of(100L)))
                .thenReturn(List.of(fromSuspendedDesigner));

        assertThat(service.recommendationsFor(customer)).isEmpty();
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
        when(ratingService.summaryFor(2L)).thenReturn(new RatingSummary(null, 0, List.of()));

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
        when(ratingService.summaryFor(2L)).thenReturn(new RatingSummary(null, 0, List.of()));

        assertThat(service.compareShortlistedDesigners(customer)).hasSize(1);
    }
}
