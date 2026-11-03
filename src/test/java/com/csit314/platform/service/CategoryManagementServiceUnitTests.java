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
        when(categoryRepository.findByNameIgnoreCase("Kitchen")).thenReturn(Optional.empty());
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
        when(categoryRepository.findByNameIgnoreCase("Kitchen")).thenReturn(Optional.of(category(1L, "Kitchen")));
        assertThatThrownBy(() -> service.addCategory("Kitchen")).isInstanceOf(ServiceException.class);
        verify(categoryRepository, never()).save(any());
    }

    // PM-13 - the only way back from a deactivation, since there's no reactivate button
    @Test
    void addCategoryReactivatesADeactivatedCategoryWithTheSameNameInsteadOfErroring() {
        Category deactivated = category(1L, "Kitchen");
        deactivated.deactivate();
        when(categoryRepository.findByNameIgnoreCase("Kitchen")).thenReturn(Optional.of(deactivated));

        Category result = service.addCategory("Kitchen");

        assertThat(result).isSameAs(deactivated);
        assertThat(result.isActive()).isTrue();
        verify(categoryRepository, never()).save(any());
    }

    // PM-03 - a plural of an existing category reuses it instead of fragmenting the
    // taxonomy with a near-identical second one
    @Test
    void addCategoryReusesAnExistingNearDuplicateInsteadOfCreatingANewOne() {
        Category existing = category(1L, "Kitchen");
        when(categoryRepository.findByNameIgnoreCase("Kitchens")).thenReturn(Optional.empty());
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(existing));

        Category result = service.addCategory("Kitchens");

        assertThat(result).isSameAs(existing);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategoryIgnoresCaseAndSpacingWhenCheckingForANearDuplicate() {
        Category existing = category(1L, "Living Room");
        when(categoryRepository.findByNameIgnoreCase("living   room")).thenReturn(Optional.empty());
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(existing));

        Category result = service.addCategory("living   room");

        assertThat(result).isSameAs(existing);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void addCategoryDoesNotMergeCategoriesThatJustSoundAlike() {
        Category existing = category(1L, "Kitchen");
        when(categoryRepository.findByNameIgnoreCase("Kitchenette")).thenReturn(Optional.empty());
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
        when(categoryRepository.findByNameIgnoreCaseAndIdNot("New name", 1L)).thenReturn(Optional.empty());

        Category result = service.renameCategory(1L, "New name");

        assertThat(result.getName()).isEqualTo("New name");
    }

    @Test
    void renameCategoryRejectsANameAlreadyUsedByAnotherActiveCategory() {
        Category category = category(1L, "Old name");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCaseAndIdNot("Living Room", 1L))
                .thenReturn(Optional.of(category(2L, "Living Room")));

        assertThatThrownBy(() -> service.renameCategory(1L, "Living Room")).isInstanceOf(ServiceException.class);
        assertThat(category.getName()).isEqualTo("Old name");
    }

    // PM-13 - renaming into a deactivated category's name can't just reactivate it the
    // way addCategory does, since that would leave two different rows with the same
    // (unique) name - so this needs a clearer message instead of the generic one
    @Test
    void renameCategoryRejectsANameBelongingToADeactivatedCategoryWithAClearerMessage() {
        Category category = category(1L, "Old name");
        Category deactivated = category(2L, "Kitchen");
        deactivated.deactivate();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCaseAndIdNot("Kitchen", 1L)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.renameCategory(1L, "Kitchen"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("deactivated");
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
