package com.csit314.platform.config;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.PhotoHashRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.service.ImageSimilarityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// PM-04
@ExtendWith(MockitoExtension.class)
class PhotoHashBackfillRunnerUnitTests {

    @Mock ProjectRepository projectRepository;
    @Mock PhotoHashRepository photoHashRepository;
    @Mock ImageSimilarityService imageSimilarityService;

    Project project;

    @BeforeEach
    void setUp() {
        InteriorDesigner designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        Category category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
    }

    @Test
    void hashesAPhotoThatHasNoHashYet(@TempDir Path uploadDir) throws Exception {
        Files.createDirectories(uploadDir.resolve("projects"));
        Files.write(uploadDir.resolve("projects/existing.jpg"), "fake-image-bytes".getBytes());
        project.addPhoto("/uploads/projects/existing.jpg");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(photoHashRepository.existsByPhotoUrl("/uploads/projects/existing.jpg")).thenReturn(false);

        PhotoHashBackfillRunner runner = new PhotoHashBackfillRunner(
                projectRepository, photoHashRepository, imageSimilarityService, uploadDir.toString());
        runner.run(null);

        verify(imageSimilarityService).recordOnly(eq(project), eq("/uploads/projects/existing.jpg"),
                eq("fake-image-bytes".getBytes()));
    }

    @Test
    void skipsAPhotoThatAlreadyHasAHash(@TempDir Path uploadDir) {
        project.addPhoto("/uploads/projects/already-hashed.jpg");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(photoHashRepository.existsByPhotoUrl("/uploads/projects/already-hashed.jpg")).thenReturn(true);

        PhotoHashBackfillRunner runner = new PhotoHashBackfillRunner(
                projectRepository, photoHashRepository, imageSimilarityService, uploadDir.toString());
        runner.run(null);

        verifyNoInteractions(imageSimilarityService);
    }

    @Test
    void skipsAPhotoWhoseFileIsMissingFromDisk(@TempDir Path uploadDir) {
        project.addPhoto("/uploads/projects/deleted-from-disk.jpg");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(photoHashRepository.existsByPhotoUrl("/uploads/projects/deleted-from-disk.jpg")).thenReturn(false);

        PhotoHashBackfillRunner runner = new PhotoHashBackfillRunner(
                projectRepository, photoHashRepository, imageSimilarityService, uploadDir.toString());
        runner.run(null);

        verifyNoInteractions(imageSimilarityService);
    }

    @Test
    void alsoBackfillsBeforePhotos(@TempDir Path uploadDir) throws Exception {
        Files.createDirectories(uploadDir.resolve("projects"));
        Files.write(uploadDir.resolve("projects/before.jpg"), "before-bytes".getBytes());
        project.addBeforePhoto("/uploads/projects/before.jpg");
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(photoHashRepository.existsByPhotoUrl("/uploads/projects/before.jpg")).thenReturn(false);

        PhotoHashBackfillRunner runner = new PhotoHashBackfillRunner(
                projectRepository, photoHashRepository, imageSimilarityService, uploadDir.toString());
        runner.run(null);

        verify(imageSimilarityService).recordOnly(eq(project), eq("/uploads/projects/before.jpg"),
                eq("before-bytes".getBytes()));
    }
}
