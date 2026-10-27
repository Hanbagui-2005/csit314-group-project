package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// PM-02/03/04
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

    @Test
    void deleteCategorySucceedsWhenNoProjectUsesIt() {
        Category category = category(1L, "Kitchen");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(projectRepository.countByCategoryId(1L)).thenReturn(0L);

        service.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    // PM-04 - deleting a category still in use would leave projects pointing at nothing
    @Test
    void deleteCategoryFailsWhenAProjectStillUsesIt() {
        Category category = category(1L, "Kitchen");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(projectRepository.countByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteCategory(1L)).isInstanceOf(ServiceException.class);
        verify(categoryRepository, never()).delete(any());
    }
}
