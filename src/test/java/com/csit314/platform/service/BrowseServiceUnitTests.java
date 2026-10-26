package com.csit314.platform.service;

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
        category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void viewingARealProjectBumpsTheCount() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        Project result = service.viewProject(100L);

        assertThat(result.getViewCount()).isEqualTo(1);
    }

    @Test
    void viewingTwiceCountsTwice() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L);
        service.viewProject(100L);

        assertThat(project.getViewCount()).isEqualTo(2);
    }

    @Test
    void viewingAProjectRecordsAViewEvent() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L);

        verify(projectViewRepository).save(any());
    }

    @Test
    void viewingAMissingProjectFails() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.viewProject(999L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void blankSearchTermsAreTreatedAsNoFilter() {
        service.search("  ", "", null, null, false);

        verify(projectRepository).search(null, null, null, null, false);
    }

    @Test
    void realSearchTermsArePassedThrough() {
        service.search(" kitchen ", "Kitchen", null, null, false);

        verify(projectRepository).search("kitchen", "Kitchen", null, null, false);
    }

    // CUS-04
    @Test
    void budgetAndRoomSizeFiltersArePassedThrough() {
        service.search(null, null, 10000, 15, false);

        verify(projectRepository).search(null, null, 10000, 15, false);
    }

    // CUS-09
    @Test
    void verifiedOnlyFlagIsPassedThrough() {
        service.search(null, null, null, null, true);

        verify(projectRepository).search(null, null, null, null, true);
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
