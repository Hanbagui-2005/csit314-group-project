package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.ProjectForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Taiga #19 / task 19.4 - project edit permission checks (IDR-07). */
@ExtendWith(MockitoExtension.class)
class ProjectServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock FileStorageService fileStorageService;
    ProjectService service;

    InteriorDesigner owner;
    InteriorDesigner someoneElse;
    Category category;
    Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, categoryRepository, fileStorageService);

        owner = new InteriorDesigner("Owner", "owner@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(owner, "id", 1L);

        someoneElse = new InteriorDesigner("Someone Else", "someone@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(someoneElse, "id", 2L);

        category = new Category("Kitchen");
        ReflectionTestUtils.setField(category, "id", 10L);

        project = new Project("Original title", "Original description", category, owner);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    private ProjectForm form(String title) {
        return new ProjectForm(title, "Updated description", 10L);
    }

    @Test
    void ownerCanUpdateTheirOwnProject() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Project result = service.updateProject(owner, 100L, form("Updated title"), null, null);

        assertThat(result.getTitle()).isEqualTo("Updated title");
        assertThat(result.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void anotherDesignerCannotUpdateSomeoneElsesProject() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.updateProject(someoneElse, 100L, form("Hacked title"), null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own projects");

        assertThat(project.getTitle()).isEqualTo("Original title");
    }

    @Test
    void updatingAMissingProjectFails() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProject(owner, 999L, form("Title"), null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateAppendsNewAfterAndBeforePhotosWithoutReplacingExisting() {
        project.addPhoto("/uploads/projects/existing-after.svg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile newAfter = new MockMultipartFile("photos", "after.svg", "image/svg+xml", "x".getBytes());
        MockMultipartFile newBefore = new MockMultipartFile("beforePhotos", "before.svg", "image/svg+xml", "y".getBytes());
        when(fileStorageService.store(newAfter)).thenReturn("/uploads/projects/new-after.svg");
        when(fileStorageService.store(newBefore)).thenReturn("/uploads/projects/new-before.svg");

        Project result = service.updateProject(owner, 100L, form("Title"), List.of(newAfter), List.of(newBefore));

        assertThat(result.getPhotoUrls())
                .containsExactly("/uploads/projects/existing-after.svg", "/uploads/projects/new-after.svg");
        assertThat(result.getBeforePhotoUrls()).containsExactly("/uploads/projects/new-before.svg");
    }

    @Test
    void updateWithoutNewPhotosLeavesExistingPhotosUntouched() {
        project.addPhoto("/uploads/projects/existing-after.svg");
        project.addBeforePhoto("/uploads/projects/existing-before.svg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Project result = service.updateProject(owner, 100L, form("Title"), null, null);

        assertThat(result.getPhotoUrls()).containsExactly("/uploads/projects/existing-after.svg");
        assertThat(result.getBeforePhotoUrls()).containsExactly("/uploads/projects/existing-before.svg");
        verify(fileStorageService, never()).store(any());
    }
}
