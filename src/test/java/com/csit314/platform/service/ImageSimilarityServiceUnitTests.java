package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.PhotoHash;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.PhotoHashRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// PM-04 - photo plagiarism check against other designers' uploads
@ExtendWith(MockitoExtension.class)
class ImageSimilarityServiceUnitTests {

    @Mock PhotoHashRepository photoHashRepository;
    @Mock ProjectFlagService projectFlagService;
    ImageSimilarityService service;

    Project project;

    @BeforeEach
    void setUp() {
        service = new ImageSimilarityService(photoHashRepository, projectFlagService);

        InteriorDesigner designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
        Category category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    private byte[] realPngBytes(int fillColor) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = image.getGraphics();
        graphics.setColor(new java.awt.Color(fillColor));
        graphics.fillRect(0, 0, 32, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void recordsTheHashOfANewPhotoWithoutFlaggingWhenNothingMatches() throws Exception {
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());

        service.checkAndRecord(project, "/uploads/a.png", realPngBytes(0x336699));

        verify(photoHashRepository).save(any(PhotoHash.class));
        verifyNoInteractions(projectFlagService);
    }

    // the hash is deterministic, so re-deriving it from the service's own first save
    // (rather than hardcoding a value) keeps this test honest about what the
    // algorithm actually produces, without hardcoding or re-implementing it here
    @Test
    void flagsTheProjectWhenAnotherDesignersPhotoHashIsAVeryCloseMatch() throws Exception {
        byte[] bytes = realPngBytes(0x336699);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());
        service.checkAndRecord(project, "/uploads/a.png", bytes);
        ArgumentCaptor<PhotoHash> captor = ArgumentCaptor.forClass(PhotoHash.class);
        verify(photoHashRepository).save(captor.capture());
        long sameImageHash = captor.getValue().getHash();

        InteriorDesigner otherDesigner = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(otherDesigner, "id", 3L);
        Project otherProject = new Project("Someone else's kitchen", "x", project.getCategory(), otherDesigner);
        PhotoHash existingMatch = new PhotoHash(otherProject, "/uploads/other.png", sameImageHash);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of(existingMatch));

        service.checkAndRecord(project, "/uploads/b.png", bytes);

        verify(projectFlagService).flagAutomatically(eq(project), contains("plagiarism"));
    }

    @Test
    void doesNotFlagAgainstTheDesignersOwnEarlierPhotos() {
        // findByProjectDesignerIdNot already excludes the uploading designer's own
        // projects at the query level - this just confirms the service asks for the
        // right designer's id
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());

        service.checkAndRecord(project, "/uploads/a.png", new byte[]{1, 2, 3});

        verify(photoHashRepository).findByProjectDesignerIdNot(2L);
        verifyNoInteractions(projectFlagService);
    }

    // PM-04 - the backfill runner uses this for pre-existing photos: a hash is still
    // recorded (so later uploads can be compared against it), but nothing gets flagged
    // retroactively just because old/seed data happens to collide
    @Test
    void recordOnlySavesTheHashWithoutEverChecking() throws Exception {
        service.recordOnly(project, "/uploads/old.png", realPngBytes(0x336699));

        verify(photoHashRepository).save(any(PhotoHash.class));
        verifyNoInteractions(projectFlagService);
        verify(photoHashRepository, never()).findByProjectDesignerIdNot(any());
    }
}
