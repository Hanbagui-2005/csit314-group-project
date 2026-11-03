package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
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

// PM-01 and the rename/delete actions that support it
@ExtendWith(MockitoExtension.class)
class CategoryManagementServiceUnitTests {

    @Mock CategoryRepository categoryRepository;
    @Mock ProjectRepository projectRepository;
    CategoryManagementService service;

    @BeforeEach
    void setUp() {
        service = new CategoryManagementService(categoryRepository, projectRepository);
    }

    private Category category(long id, String name) {
        Category category = new Category(name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Test
    void addCategorySavesATrimmedName() {
        when(categoryRepository.existsByNameIgnoreCase("Kitchen")).thenReturn(false);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of());
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.addCategory("  Kitchen  ");

        assertThat(result.getName()).isEqualTo("Kitchen");
    }

    @Test
    void addCategoryRejectsABlankName() {
        assertThatThrownBy(() -> service.addCategory("   ")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void addCategoryRejectsADuplicateName() {
        when(categoryRepository.existsByNameIgnoreCase("Kitchen")).thenReturn(true);
        assertThatThrownBy(() -> service.addCategory("Kitchen")).isInstanceOf(ServiceException.class);
        verify(categoryRepository, never()).save(any());
    }

    // PM-03 - a plural of an existing category reuses it instead of fragmenting the
    // taxonomy with a near-identical second one
    @Test
    void addCategoryReusesAnExistingNearDuplicateInsteadOfCreatingANewOne() {
        Category existing = category(1L, "Kitchen");
        when(categoryRepository.existsByNameIgnoreCase("Kitchens")).thenReturn(false);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(existing));

        Category result = service.addCategory("Kitchens");

        assertThat(result).isSameAs(existing);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategoryIgnoresCaseAndSpacingWhenCheckingForANearDuplicate() {
        Category existing = category(1L, "Living Room");
        when(categoryRepository.existsByNameIgnoreCase("living   room")).thenReturn(false);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(existing));

        Category result = service.addCategory("living   room");

        assertThat(result).isSameAs(existing);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategoryDoesNotMergeCategoriesThatJustSoundAlike() {
        Category existing = category(1L, "Kitchen");
        when(categoryRepository.existsByNameIgnoreCase("Kitchenette")).thenReturn(false);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(existing));
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = service.addCategory("Kitchenette");

        assertThat(result.getName()).isEqualTo("Kitchenette");
        verify(categoryRepository).save(any());
    }

    @Test
    void renameCategoryUpdatesTheName() {
        Category category = category(1L, "Old name");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New name", 1L)).thenReturn(false);

        Category result = service.renameCategory(1L, "New name");

        assertThat(result.getName()).isEqualTo("New name");
    }

    @Test
    void renameCategoryRejectsANameAlreadyUsedByAnotherCategory() {
        Category category = category(1L, "Old name");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Living Room", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.renameCategory(1L, "Living Room")).isInstanceOf(ServiceException.class);
        assertThat(category.getName()).isEqualTo("Old name");
    }

    @Test
    void renameCategoryFailsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.renameCategory(1L, "New name")).isInstanceOf(ServiceException.class);
    }

    // PM-13 - a soft delete, so a category still referenced by projects can still be
    // deactivated without breaking them
    @Test
    void deleteCategoryDeactivatesInsteadOfRemovingTheRow() {
        Category category = category(1L, "Kitchen");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        service.deleteCategory(1L);

        assertThat(category.isActive()).isFalse();
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryFailsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteCategory(1L)).isInstanceOf(ServiceException.class);
    }

    // PM-03 - cleans up categories that fragmented before this check existed, not just
    // ones blocked going forward by addCategory
    @Test
    void mergeDuplicateCategoriesReassignsProjectsAndDeactivatesTheLoser() {
        Category kitchen = category(1L, "Kitchen");
        Category kitchens = category(2L, "Kitchens");
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(kitchen, kitchens));
        Project project = mock(Project.class);
        when(projectRepository.findByCategoryId(2L)).thenReturn(List.of(project));

        service.mergeDuplicateCategories();

        verify(project).reassignCategory(kitchen);
        assertThat(kitchens.isActive()).isFalse();
        assertThat(kitchen.isActive()).isTrue();
    }

    @Test
    void mergeDuplicateCategoriesLeavesUnrelatedCategoriesAlone() {
        Category kitchen = category(1L, "Kitchen");
        Category bathroom = category(2L, "Bathroom");
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(kitchen, bathroom));

        service.mergeDuplicateCategories();

        assertThat(kitchen.isActive()).isTrue();
        assertThat(bathroom.isActive()).isTrue();
        verifyNoInteractions(projectRepository);
    }
}
