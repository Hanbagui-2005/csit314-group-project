package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.MilestoneStage;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.dto.ProjectForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Taiga #19 / task 19.4 - project edit permission checks (IDR-07). */
@ExtendWith(MockitoExtension.class)
class ProjectServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock FileStorageService fileStorageService;
    @Mock ImageSimilarityService imageSimilarityService;
    ProjectService service;

    InteriorDesigner owner;
    InteriorDesigner someoneElse;
    Category category;
    Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, categoryRepository, fileStorageService, imageSimilarityService);

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
        return form(title, null, null, null);
    }

    private ProjectForm form(String title, Integer budgetMin, Integer budgetMax, String estimatedTimeline) {
        return new ProjectForm(title, "Updated description", 10L, budgetMin, budgetMax, estimatedTimeline, null);
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

    // IDR-09 - milestone tracking
    @Test
    void advanceMilestoneMovesToTheNextStage() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        Project result = service.advanceMilestone(owner, 100L);

        assertThat(result.getMilestoneStage()).isEqualTo(MilestoneStage.MATERIALS_ORDERED);
    }

    @Test
    void advanceMilestoneStopsAtTheLastStage() {
        project.advanceMilestone();
        project.advanceMilestone();
        assertThat(project.getMilestoneStage()).isEqualTo(MilestoneStage.INSTALLATION_COMPLETE);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.advanceMilestone(owner, 100L))
                .isInstanceOf(ServiceException.class);
        assertThat(project.getMilestoneStage()).isEqualTo(MilestoneStage.INSTALLATION_COMPLETE);
    }

    @Test
    void anotherDesignerCannotAdvanceSomeoneElsesMilestone() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.advanceMilestone(someoneElse, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own projects");
        assertThat(project.getMilestoneStage()).isEqualTo(MilestoneStage.DESIGN_APPROVED);
    }

    // IDR-14 - budget range + estimated timeline
    @Test
    void updateSavesAValidBudgetRangeAndTimeline() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Project result = service.updateProject(owner, 100L, form("Title", 5000, 8000, "4-6 weeks"), null, null);

        assertThat(result.getBudgetMin()).isEqualTo(5000);
        assertThat(result.getBudgetMax()).isEqualTo(8000);
        assertThat(result.getEstimatedTimeline()).isEqualTo("4-6 weeks");
    }

    @Test
    void updateWithNoBudgetLeavesItBlank() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Project result = service.updateProject(owner, 100L, form("Title"), null, null);

        assertThat(result.getBudgetMin()).isNull();
        assertThat(result.getBudgetMax()).isNull();
    }

    @Test
    void updateRejectsAOneSidedBudgetRange() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.updateProject(owner, 100L, form("Title", 5000, null, null), null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("both");
    }

    @Test
    void updateRejectsAMinAboveMax() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.updateProject(owner, 100L, form("Title", 9000, 5000, null), null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Minimum");
    }

    @Test
    void updateRejectsAZeroOrNegativeRoomSize() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        ProjectForm negativeRoomSize = new ProjectForm("Title", "Updated description", 10L, null, null, null, -5);

        assertThatThrownBy(() -> service.updateProject(owner, 100L, negativeRoomSize, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Room size");
    }

    @Test
    void createRejectsAZeroOrNegativeRoomSize() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile photo = new MockMultipartFile("photos", "a.png", "image/png", "x".getBytes());
        ProjectForm zeroRoomSize = new ProjectForm("Title", "Updated description", 10L, null, null, null, 0);

        assertThatThrownBy(() -> service.createProject(owner, zeroRoomSize, List.of(photo), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Room size");
        verify(fileStorageService, never()).store(any());
    }

    // IDR-15 - photo file validation is delegated to FileStorageService.validate();
    // this just confirms ProjectService checks every file before storing or saving anything
    // (the actual type/size rules are tested directly in FileStorageServiceUnitTests)
    @Test
    void createRejectsABadFileBeforeStoringOrSavingAnything() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile badPhoto = new MockMultipartFile("photos", "b.exe", "application/octet-stream", "y".getBytes());
        doThrow(new ServiceException("\"b.exe\" isn't a supported image type."))
                .when(fileStorageService).validate(badPhoto);

        assertThatThrownBy(() -> service.createProject(owner, form("Title"), List.of(badPhoto), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supported image type");
        verify(fileStorageService, never()).store(any());
        verify(projectRepository, never()).save(any());
    }

    // PM-04 - every uploaded photo is checked against the rest of the platform once the
    // project has been saved (and so has an id/real photo URLs to check with)
    @Test
    void createChecksEveryUploadedPhotoForPlagiarism() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile photo = new MockMultipartFile("photos", "a.png", "image/png", "real-bytes".getBytes());
        when(fileStorageService.store(photo)).thenReturn("/uploads/projects/a.png");
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createProject(owner, form("Title"), List.of(photo), null);

        ArgumentCaptor<List<byte[]>> bytesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageSimilarityService).checkAndRecord(any(Project.class), eq(List.of("/uploads/projects/a.png")),
                bytesCaptor.capture());
        assertThat(bytesCaptor.getValue()).hasSize(1);
        assertThat(bytesCaptor.getValue().get(0)).isEqualTo("real-bytes".getBytes());
    }

    @Test
    void updateChecksOnlyTheNewlyAddedPhotosForPlagiarism() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        project.addPhoto("/uploads/projects/existing.png");
        MockMultipartFile newPhoto = new MockMultipartFile("photos", "b.png", "image/png", "new-bytes".getBytes());
        when(fileStorageService.store(newPhoto)).thenReturn("/uploads/projects/b.png");

        service.updateProject(owner, 100L, form("Title"), List.of(newPhoto), null);

        ArgumentCaptor<List<byte[]>> bytesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageSimilarityService).checkAndRecord(eq(project), eq(List.of("/uploads/projects/b.png")),
                bytesCaptor.capture());
        assertThat(bytesCaptor.getValue().get(0)).isEqualTo("new-bytes".getBytes());
    }

    // PM-04 - before photos can be plagiarised too, same as the "after" ones. Checked
    // together with the after photos in one batch (Anthony's review), not two
    // separate calls
    @Test
    void createChecksBeforePhotosForPlagiarismToo() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile after = new MockMultipartFile("photos", "a.png", "image/png", "after-bytes".getBytes());
        MockMultipartFile before = new MockMultipartFile("beforePhotos", "b.png", "image/png", "before-bytes".getBytes());
        when(fileStorageService.store(after)).thenReturn("/uploads/projects/a.png");
        when(fileStorageService.store(before)).thenReturn("/uploads/projects/before.png");
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createProject(owner, form("Title"), List.of(after), List.of(before));

        ArgumentCaptor<List<byte[]>> bytesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageSimilarityService).checkAndRecord(any(Project.class),
                eq(List.of("/uploads/projects/a.png", "/uploads/projects/before.png")), bytesCaptor.capture());
        assertThat(bytesCaptor.getValue()).hasSize(2);
        assertThat(bytesCaptor.getValue().get(0)).isEqualTo("after-bytes".getBytes());
        assertThat(bytesCaptor.getValue().get(1)).isEqualTo("before-bytes".getBytes());
    }

    @Test
    void updateChecksOnlyTheNewlyAddedBeforePhotosForPlagiarism() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        project.addBeforePhoto("/uploads/projects/existing-before.png");
        MockMultipartFile newBefore = new MockMultipartFile("beforePhotos", "c.png", "image/png", "new-before".getBytes());
        when(fileStorageService.store(newBefore)).thenReturn("/uploads/projects/c.png");

        service.updateProject(owner, 100L, form("Title"), null, List.of(newBefore));

        ArgumentCaptor<List<byte[]>> bytesCaptor = ArgumentCaptor.forClass(List.class);
        verify(imageSimilarityService).checkAndRecord(eq(project), eq(List.of("/uploads/projects/c.png")),
                bytesCaptor.capture());
        assertThat(bytesCaptor.getValue().get(0)).isEqualTo("new-before".getBytes());
    }

    // PM-13
    @Test
    void createRejectsADeactivatedCategory() {
        category.deactivate();
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        MockMultipartFile photo = new MockMultipartFile("photos", "a.png", "image/png", "x".getBytes());

        assertThatThrownBy(() -> service.createProject(owner, form("Title"), List.of(photo), null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no longer available");
        verify(fileStorageService, never()).store(any());
    }

    @Test
    void updateAllowsKeepingTheProjectsCurrentCategoryEvenIfItsSinceBeenDeactivated() {
        category.deactivate();
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Project result = service.updateProject(owner, 100L, form("Updated title"), null, null);

        assertThat(result.getCategory()).isEqualTo(category);
    }

    @Test
    void updateRejectsSwitchingToADifferentDeactivatedCategory() {
        Category otherCategory = new Category("Bathroom");
        ReflectionTestUtils.setField(otherCategory, "id", 20L);
        otherCategory.deactivate();
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(otherCategory));
        ProjectForm switchToDeactivated = new ProjectForm("Title", "Updated description", 20L, null, null, null, null);

        assertThatThrownBy(() -> service.updateProject(owner, 100L, switchToDeactivated, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no longer available");
        assertThat(project.getCategory()).isEqualTo(category);
    }

    // PM-13 - without this, the edit form's dropdown wouldn't include the project's
    // current category once it's deactivated, nothing would be selected, the browser
    // would default to the first option, and saving any other edit would silently
    // move the project to an unrelated category
    @Test
    void listCategoriesForEditIncludesTheProjectsOwnDeactivatedCategory() {
        category.deactivate();
        Category activeOne = new Category("Bathroom");
        ReflectionTestUtils.setField(activeOne, "id", 20L);
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(activeOne));

        List<Category> result = service.listCategoriesForEdit(project);

        assertThat(result).containsExactlyInAnyOrder(activeOne, category);
    }

    @Test
    void listCategoriesForEditDoesNotDuplicateAnAlreadyActiveCategory() {
        when(categoryRepository.findByActiveTrue(any())).thenReturn(List.of(category));

        List<Category> result = service.listCategoriesForEdit(project);

        assertThat(result).containsExactly(category);
    }

    // ---- deleting a project / editing its photos ----

    @Test
    void ownerCanDeleteTheirProjectAndItsPhotoFilesAreRemoved() {
        project.addPhoto("/uploads/projects/a.jpg");
        project.addBeforePhoto("/uploads/projects/b.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        String title = service.deleteProject(owner, 100L);

        assertThat(title).isEqualTo("Original title");
        verify(projectRepository).deleteShortlistsFor(100L);
        verify(projectRepository).deleteViewsFor(100L);
        verify(projectRepository).deleteFlagsFor(100L);
        verify(projectRepository).deletePhotoHashesFor(100L);
        verify(projectRepository).unlinkBookingsFrom(100L);
        verify(projectRepository).unlinkMessagesFrom(100L);
        verify(projectRepository).delete(project);
        verify(fileStorageService).delete("/uploads/projects/a.jpg");
        verify(fileStorageService).delete("/uploads/projects/b.jpg");
    }

    @Test
    void anotherDesignerCannotDeleteSomeoneElsesProject() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deleteProject(someoneElse, 100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own projects");

        verify(projectRepository, never()).delete(any(Project.class));
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void ownerCanDeleteOneOfSeveralAfterPhotos() {
        project.addPhoto("/uploads/projects/a.jpg");
        project.addPhoto("/uploads/projects/b.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.deletePhoto(owner, 100L, "/uploads/projects/a.jpg", false);

        assertThat(project.getPhotoUrls()).containsExactly("/uploads/projects/b.jpg");
        verify(projectRepository).deletePhotoHashByUrl("/uploads/projects/a.jpg");
        verify(fileStorageService).delete("/uploads/projects/a.jpg");
    }

    @Test
    void theLastAfterPhotoCannotBeDeleted() {
        project.addPhoto("/uploads/projects/only.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deletePhoto(owner, 100L, "/uploads/projects/only.jpg", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("at least one photo");

        assertThat(project.getPhotoUrls()).containsExactly("/uploads/projects/only.jpg");
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void theLastBeforePhotoCanBeDeletedSinceBeforePhotosAreOptional() {
        project.addPhoto("/uploads/projects/after.jpg");
        project.addBeforePhoto("/uploads/projects/before.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.deletePhoto(owner, 100L, "/uploads/projects/before.jpg", true);

        assertThat(project.getBeforePhotoUrls()).isEmpty();
        assertThat(project.getPhotoUrls()).containsExactly("/uploads/projects/after.jpg");
    }

    @Test
    void deletingAPhotoThatIsNotOnTheProjectFails() {
        project.addPhoto("/uploads/projects/a.jpg");
        project.addPhoto("/uploads/projects/b.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        // an "after" url sent as a before-photo matches neither list
        assertThatThrownBy(() -> service.deletePhoto(owner, 100L, "/uploads/projects/a.jpg", true))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("isn't part of this project");
        assertThatThrownBy(() -> service.deletePhoto(owner, 100L, "/uploads/projects/other.jpg", false))
                .isInstanceOf(ServiceException.class);

        assertThat(project.getPhotoUrls()).hasSize(2);
    }

    @Test
    void anotherDesignerCannotDeleteAPhoto() {
        project.addPhoto("/uploads/projects/a.jpg");
        project.addPhoto("/uploads/projects/b.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.deletePhoto(someoneElse, 100L, "/uploads/projects/a.jpg", false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own projects");
        assertThat(project.getPhotoUrls()).hasSize(2);
    }

    @Test
    void replacingAPhotoKeepsItsPositionAndChecksTheNewUpload() {
        project.addPhoto("/uploads/projects/a.jpg");
        project.addPhoto("/uploads/projects/b.jpg");
        project.addPhoto("/uploads/projects/c.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        MockMultipartFile replacement = new MockMultipartFile("photo", "new.png", "image/png", "z".getBytes());
        when(fileStorageService.store(replacement)).thenReturn("/uploads/projects/new.png");

        service.replacePhoto(owner, 100L, "/uploads/projects/b.jpg", false, replacement);

        assertThat(project.getPhotoUrls()).containsExactly(
                "/uploads/projects/a.jpg", "/uploads/projects/new.png", "/uploads/projects/c.jpg");
        verify(fileStorageService).validate(replacement);
        verify(projectRepository).deletePhotoHashByUrl("/uploads/projects/b.jpg");
        verify(imageSimilarityService).checkAndRecord(eq(project), eq(List.of("/uploads/projects/new.png")), any());
        verify(fileStorageService).delete("/uploads/projects/b.jpg");
    }

    @Test
    void replacingABeforePhotoOnlyTouchesTheBeforeList() {
        project.addPhoto("/uploads/projects/after.jpg");
        project.addBeforePhoto("/uploads/projects/before.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        MockMultipartFile replacement = new MockMultipartFile("photo", "new.png", "image/png", "z".getBytes());
        when(fileStorageService.store(replacement)).thenReturn("/uploads/projects/new-before.png");

        service.replacePhoto(owner, 100L, "/uploads/projects/before.jpg", true, replacement);

        assertThat(project.getBeforePhotoUrls()).containsExactly("/uploads/projects/new-before.png");
        assertThat(project.getPhotoUrls()).containsExactly("/uploads/projects/after.jpg");
    }

    @Test
    void replacingWithoutChoosingAFileFails() {
        project.addPhoto("/uploads/projects/a.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        MockMultipartFile empty = new MockMultipartFile("photo", "", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.replacePhoto(owner, 100L, "/uploads/projects/a.jpg", false, empty))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Choose a new photo");
        assertThatThrownBy(() -> service.replacePhoto(owner, 100L, "/uploads/projects/a.jpg", false, null))
                .isInstanceOf(ServiceException.class);

        verify(fileStorageService, never()).store(any());
    }

    @Test
    void anInvalidReplacementLeavesTheOriginalPhotoInPlace() {
        project.addPhoto("/uploads/projects/a.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        MockMultipartFile bad = new MockMultipartFile("photo", "evil.html", "text/html", "<script>".getBytes());
        doThrow(new ServiceException("not a supported image type")).when(fileStorageService).validate(bad);

        assertThatThrownBy(() -> service.replacePhoto(owner, 100L, "/uploads/projects/a.jpg", false, bad))
                .isInstanceOf(ServiceException.class);

        assertThat(project.getPhotoUrls()).containsExactly("/uploads/projects/a.jpg");
        verify(fileStorageService, never()).store(any());
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void anotherDesignerCannotReplaceAPhoto() {
        project.addPhoto("/uploads/projects/a.jpg");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        MockMultipartFile replacement = new MockMultipartFile("photo", "new.png", "image/png", "z".getBytes());

        assertThatThrownBy(() -> service.replacePhoto(someoneElse, 100L, "/uploads/projects/a.jpg", false, replacement))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("your own projects");
        verify(fileStorageService, never()).store(any());
    }
}
