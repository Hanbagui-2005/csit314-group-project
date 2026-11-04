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
import java.util.Random;

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

    // a seeded per-pixel random pattern, not a flat fill - gives the image real
    // brightness variance (close to the full 0-255 range) so it hashes normally.
    // Deterministic per seed: the same seed always produces the same bytes/hash
    private byte[] variedPngBytes(long seed) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int gray = random.nextInt(256);
                image.setRGB(x, y, new java.awt.Color(gray, gray, gray).getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // Anthony's review - a flat, single-colour image (what the old test helper here
    // produced) is exactly the "too uniform to hash reliably" case that's now skipped
    // on purpose; used only by the test that specifically covers that behaviour
    private byte[] solidPngBytes(int fillColor) throws Exception {
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

        service.checkAndRecord(project, List.of("/uploads/a.png"), List.of(variedPngBytes(1)));

        verify(photoHashRepository).save(any(PhotoHash.class));
        verifyNoInteractions(projectFlagService);
    }

    // the hash is deterministic, so re-deriving it from the service's own first save
    // (rather than hardcoding a value) keeps this test honest about what the
    // algorithm actually produces, without hardcoding or re-implementing it here
    @Test
    void flagsTheProjectWhenAnotherDesignersPhotoHashIsAVeryCloseMatch() throws Exception {
        byte[] bytes = variedPngBytes(1);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());
        service.checkAndRecord(project, List.of("/uploads/a.png"), List.of(bytes));
        ArgumentCaptor<PhotoHash> captor = ArgumentCaptor.forClass(PhotoHash.class);
        verify(photoHashRepository).save(captor.capture());
        long sameImageHash = captor.getValue().getHash();

        InteriorDesigner otherDesigner = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(otherDesigner, "id", 3L);
        Project otherProject = new Project("Someone else's kitchen", "x", project.getCategory(), otherDesigner);
        PhotoHash existingMatch = new PhotoHash(otherProject, "/uploads/other.png", sameImageHash);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of(existingMatch));

        service.checkAndRecord(project, List.of("/uploads/b.png"), List.of(bytes));

        verify(projectFlagService).flagAutomatically(eq(project), contains("plagiarism"));
    }

    @Test
    void doesNotFlagAgainstTheDesignersOwnEarlierPhotos() {
        // findByProjectDesignerIdNot already excludes the uploading designer's own
        // projects at the query level - this just confirms the service asks for the
        // right designer's id
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());

        service.checkAndRecord(project, List.of("/uploads/a.png"), List.of(new byte[]{1, 2, 3}));

        verify(photoHashRepository).findByProjectDesignerIdNot(2L);
        verifyNoInteractions(projectFlagService);
    }

    // Anthony's review - this used to call findByProjectDesignerIdNot once per photo
    // (a full-table scan per photo instead of per upload) and could flag the project
    // once per matching photo instead of once for the whole upload
    @Test
    void loadsTheOtherDesignersHashesOnlyOnceForTheWholeBatch() throws Exception {
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());

        service.checkAndRecord(project, List.of("/uploads/a.png", "/uploads/b.png", "/uploads/c.png"),
                List.of(variedPngBytes(1), variedPngBytes(2), variedPngBytes(3)));

        verify(photoHashRepository, times(1)).findByProjectDesignerIdNot(2L);
        verify(photoHashRepository, times(3)).save(any(PhotoHash.class));
    }

    @Test
    void flagsTheProjectAtMostOnceEvenWhenSeveralPhotosInTheBatchMatch() throws Exception {
        byte[] bytes = variedPngBytes(1);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());
        service.checkAndRecord(project, List.of("/uploads/seed.png"), List.of(bytes));
        ArgumentCaptor<PhotoHash> captor = ArgumentCaptor.forClass(PhotoHash.class);
        verify(photoHashRepository).save(captor.capture());
        long sameImageHash = captor.getValue().getHash();

        InteriorDesigner otherDesigner = new InteriorDesigner("Other", "other@example.test", "encoded",
                new Company("Other Studio", "Description", "67890"));
        ReflectionTestUtils.setField(otherDesigner, "id", 3L);
        Project otherProject = new Project("Someone else's kitchen", "x", project.getCategory(), otherDesigner);
        PhotoHash existingMatch = new PhotoHash(otherProject, "/uploads/other.png", sameImageHash);
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of(existingMatch));

        // every photo in this batch matches the same existing hash
        service.checkAndRecord(project, List.of("/uploads/b.png", "/uploads/c.png"), List.of(bytes, bytes));

        verify(projectFlagService, times(1)).flagAutomatically(eq(project), contains("plagiarism"));
    }

    // Anthony's review - an image that fails to decode used to be stored with hash 0,
    // which then falsely matched any other photo whose own hash happened to have 5 or
    // fewer bits set. It's skipped entirely now instead
    @Test
    void skipsAPhotoThatFailsToDecodeInsteadOfRecordingAFakeHash() {
        service.checkAndRecord(project, List.of("/uploads/not-an-image.png"), List.of(new byte[]{1, 2, 3}));

        verify(photoHashRepository, never()).save(any());
        verifyNoInteractions(projectFlagService);
    }

    // Anthony's review - a plain/near-uniform photo (a blank wall, an overexposed
    // shot) has almost every pixel on the same side of its own average, so aHash gave
    // it a hash near all-0s or all-1s. Any two such photos then landed within the
    // Hamming threshold of each other purely for being "flat", not for being the same
    // photo - a false plagiarism flag. Skipped entirely now instead of hashed
    @Test
    void skipsAPlainOrNearUniformPhotoInsteadOfRiskingAFalseMatch() throws Exception {
        when(photoHashRepository.findByProjectDesignerIdNot(2L)).thenReturn(List.of());

        service.checkAndRecord(project, List.of("/uploads/blank-wall.png"), List.of(solidPngBytes(0x808080)));

        verify(photoHashRepository, never()).save(any());
        verifyNoInteractions(projectFlagService);
    }

    @Test
    void doesNothingForAnEmptyBatch() {
        service.checkAndRecord(project, List.of(), List.of());

        verifyNoInteractions(photoHashRepository, projectFlagService);
    }

    // PM-04 - the backfill runner uses this for pre-existing photos: a hash is still
    // recorded (so later uploads can be compared against it), but nothing gets flagged
    // retroactively just because old/seed data happens to collide
    @Test
    void recordOnlySavesTheHashWithoutEverChecking() throws Exception {
        service.recordOnly(project, "/uploads/old.png", variedPngBytes(1));

        verify(photoHashRepository).save(any(PhotoHash.class));
        verifyNoInteractions(projectFlagService);
        verify(photoHashRepository, never()).findByProjectDesignerIdNot(any());
    }

    @Test
    void recordOnlySkipsAPhotoThatFailsToDecode() {
        service.recordOnly(project, "/uploads/not-an-image.png", new byte[]{1, 2, 3});

        verify(photoHashRepository, never()).save(any());
    }

    @Test
    void recordOnlySkipsAPlainOrNearUniformPhoto() throws Exception {
        service.recordOnly(project, "/uploads/blank-wall.png", solidPngBytes(0x808080));

        verify(photoHashRepository, never()).save(any());
    }
}
