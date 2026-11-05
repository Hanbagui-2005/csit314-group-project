package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #22 [IDR-10] - real view counts, and the Browse search behind it. */
@ExtendWith(MockitoExtension.class)
class BrowseServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectViewRepository projectViewRepository;
    BrowseService service;

    Project project;
    Category category;
    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new BrowseService(projectRepository, projectViewRepository);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        designer.setStatus(AccountStatus.ACTIVE);
        category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void viewingARealProjectBumpsTheCount() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L, null, true);

        // done as an atomic "view_count + 1" in the database, not by dirtying the loaded
        // entity - that full-row write could overwrite a concurrent milestone/budget edit
        verify(projectRepository).incrementViewCount(100L);
    }

    @Test
    void viewingTwiceCountsTwiceWhenEachCallIsItsOwnSession() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L, null, true);
        service.viewProject(100L, null, true);

        verify(projectRepository, times(2)).incrementViewCount(100L);
    }

    @Test
    void viewingAProjectRecordsAViewEvent() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L, null, true);

        verify(projectViewRepository).save(any());
    }

    @Test
    void viewingAMissingProjectFails() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.viewProject(999L, null, true))
                .isInstanceOf(ServiceException.class);
    }

    // IDR-10 - the designer viewing their own project shouldn't inflate their own count
    @Test
    void theDesignerViewingTheirOwnProjectDoesNotBumpTheCount() {
        ReflectionTestUtils.setField(designer, "id", 50L);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L, 50L, true);

        verify(projectRepository, never()).incrementViewCount(any());
        verifyNoInteractions(projectViewRepository);
    }

    // IDR-10 - a refresh in the same browser session shouldn't count as a second view
    @Test
    void aRepeatViewInTheSameSessionDoesNotCount() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L, null, false);

        verify(projectRepository, never()).incrementViewCount(any());
        verifyNoInteractions(projectViewRepository);
    }

    // a suspended/pending/rejected designer's project detail page should behave the same
    // as it being missing from search - hidden, and not even counted as a view
    @Test
    void viewingAProjectFromADesignerWhoIsntActiveFails() {
        designer.setStatus(AccountStatus.SUSPENDED);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.viewProject(100L, null, true))
                .isInstanceOf(ServiceException.class);
        verify(projectRepository, never()).incrementViewCount(any());
        verifyNoInteractions(projectViewRepository);
    }

    // the designer themself can still see their own project even while it's hidden from
    // everyone else - same carve-out as the view-count check above
    @Test
    void theOwnerDesignerCanStillViewTheirOwnProjectWhileNotActive() {
        ReflectionTestUtils.setField(designer, "id", 50L);
        designer.setStatus(AccountStatus.SUSPENDED);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        Project result = service.viewProject(100L, 50L, true);

        assertThat(result).isSameAs(project);
    }

    @Test
    void blankSearchTermsAreTreatedAsNoFilter() {
        service.search("  ", "", null, null, null, false, "  ");

        verify(projectRepository).search(null, null, null, null, null, false, null);
    }

    @Test
    void realSearchTermsArePassedThrough() {
        service.search(" kitchen ", "Kitchen", null, null, null, false, null);

        verify(projectRepository).search("kitchen", "Kitchen", null, null, null, false, null);
    }

    // CUS-04
    @Test
    void budgetAndRoomSizeFiltersArePassedThrough() {
        service.search(null, null, 5000, 10000, 15, false, null);

        verify(projectRepository).search(null, null, 5000, 10000, 15, false, null);
    }

    // Anthony's review on PR #27 - without this, a minBudget above maxBudget just
    // silently returned an empty result list with no explanation to the customer
    @Test
    void rejectsAMinBudgetAboveTheMaxBudget() {
        assertThatThrownBy(() -> service.search(null, null, 10000, 5000, null, false, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Minimum budget can't be more than the maximum");

        verifyNoInteractions(projectRepository);
    }

    @Test
    void anEqualMinAndMaxBudgetIsAllowed() {
        service.search(null, null, 5000, 5000, null, false, null);

        verify(projectRepository).search(null, null, 5000, 5000, null, false, null);
    }

    // CUS-09
    @Test
    void verifiedOnlyFlagIsPassedThrough() {
        service.search(null, null, null, null, null, true, null);

        verify(projectRepository).search(null, null, null, null, null, true, null);
    }

    // PM-05
    @Test
    void locationFilterIsTrimmedAndPassedThrough() {
        service.search(null, null, null, null, null, false, " Downtown ");

        verify(projectRepository).search(null, null, null, null, null, false, "Downtown");
    }

    // without this, typing "%" into either box would act as a wildcard and match
    // every project/designer instead of being searched for literally
    @Test
    void wildcardCharactersInTheSearchTermAreEscaped() {
        service.search("50% off_deal", null, null, null, null, false, null);

        verify(projectRepository).search("50!% off!_deal", null, null, null, null, false, null);
    }

    @Test
    void wildcardCharactersInTheLocationAreEscaped() {
        service.search(null, null, null, null, null, false, "100% _downtown_");

        verify(projectRepository).search(null, null, null, null, null, false, "100!% !_downtown!_");
    }

    // CUS-12
    @Test
    void estimateSpansTheWidestMinAndMaxAcrossListings() {
        Project cheap = new Project("Budget kitchen", "x", category, designer);
        ReflectionTestUtils.setField(cheap, "budgetMin", 2000);
        ReflectionTestUtils.setField(cheap, "budgetMax", 4000);
        Project premium = new Project("Premium kitchen", "x", category, designer);
        ReflectionTestUtils.setField(premium, "budgetMin", 8000);
        ReflectionTestUtils.setField(premium, "budgetMax", 15000);
        when(projectRepository.findByCategoryNameAndBudgetMinIsNotNull("Kitchen")).thenReturn(List.of(cheap, premium));

        var estimate = service.estimateBudget("Kitchen");

        assertThat(estimate).isPresent();
        assertThat(estimate.get().min()).isEqualTo(2000);
        assertThat(estimate.get().max()).isEqualTo(15000);
        assertThat(estimate.get().sampleSize()).isEqualTo(2);
    }

    @Test
    void estimateIsEmptyWithNoBudgetDataForTheCategory() {
        when(projectRepository.findByCategoryNameAndBudgetMinIsNotNull("Kitchen")).thenReturn(List.of());

        assertThat(service.estimateBudget("Kitchen")).isEmpty();
    }

    @Test
    void estimateIsEmptyWithNoCategorySelected() {
        assertThat(service.estimateBudget(null)).isEmpty();
        assertThat(service.estimateBudget("  ")).isEmpty();
        verifyNoInteractions(projectRepository);
    }
}
