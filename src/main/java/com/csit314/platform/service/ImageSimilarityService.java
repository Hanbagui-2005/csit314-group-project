package com.csit314.platform.service;

import com.csit314.platform.model.PhotoHash;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.PhotoHashRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

// PM-04 - "reverse-image-search uploaded portfolio photos so designers can't
// plagiarize someone else's work as their own". A real reverse-image search against the
// open web needs a paid external API (Google Vision, TinEye) with no fit for a
// coursework budget or timeline. This instead checks a new upload against every photo
// already on the platform itself using a perceptual hash: two images that look alike
// produce near-identical hashes even after a resize or re-compression, which is exactly
// the kind of "same photo, re-uploaded as someone else's" plagiarism the story describes
@Service
public class ImageSimilarityService {

    private static final int HASH_SIZE = 8;
    // out of 64 bits - low enough that two unrelated photos essentially never collide by
    // chance, high enough to survive a resize or re-save at different quality/format
    private static final int HAMMING_DISTANCE_THRESHOLD = 5;

    private final PhotoHashRepository photoHashRepository;
    private final ProjectFlagService projectFlagService;

    public ImageSimilarityService(PhotoHashRepository photoHashRepository, ProjectFlagService projectFlagService) {
        this.photoHashRepository = photoHashRepository;
        this.projectFlagService = projectFlagService;
    }

    // checks a newly-uploaded photo against every other designer's photos, flags the
    // project automatically on a close match, then records this photo's own hash so
    // later uploads can be compared against it too
    @Transactional
    public void checkAndRecord(Project project, String photoUrl, byte[] imageBytes) {
        long hash = computeHash(imageBytes);

        Optional<PhotoHash> match = photoHashRepository.findByProjectDesignerIdNot(project.getDesigner().getId())
                .stream()
                .filter(existing -> Long.bitCount(existing.getHash() ^ hash) <= HAMMING_DISTANCE_THRESHOLD)
                .findFirst();

        match.ifPresent(existing -> projectFlagService.flagAutomatically(project,
                "Possible plagiarism: this photo closely matches an existing photo on another designer's "
                        + "project (\"" + existing.getProject().getTitle() + "\")."));

        photoHashRepository.save(new PhotoHash(project, photoUrl, hash));
    }

    // PM-04 - backfill-only: hashes a photo that predates this feature so later uploads
    // can be compared against it, without flagging anything for it retroactively. A
    // one-off scan over old (and, for a group project, probably-similar seed/demo) data
    // isn't a real plagiarism signal the same way a brand new upload matching something
    // already-known is - it would just be noise in PM-11's review queue
    @Transactional
    public void recordOnly(Project project, String photoUrl, byte[] imageBytes) {
        photoHashRepository.save(new PhotoHash(project, photoUrl, computeHash(imageBytes)));
    }

    // average hash (aHash): shrink to 8x8 greyscale, then one bit per pixel for
    // whether it's lighter or darker than the image's own average brightness. Crude
    // compared to a perceptual (DCT-based) hash, but simple, fast, and good enough to
    // catch a re-uploaded or lightly-edited copy of the same photo
    private long computeHash(byte[] imageBytes) {
        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            return 0;
        }
        if (original == null) {
            return 0;
        }

        BufferedImage small = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        small.getGraphics().drawImage(
                original.getScaledInstance(HASH_SIZE, HASH_SIZE, Image.SCALE_SMOOTH), 0, 0, null);

        int[] brightness = new int[HASH_SIZE * HASH_SIZE];
        long total = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                int value = new Color(small.getRGB(x, y)).getRed(); // grayscale: R=G=B
                brightness[y * HASH_SIZE + x] = value;
                total += value;
            }
        }
        int average = (int) (total / brightness.length);

        long hash = 0;
        for (int i = 0; i < brightness.length; i++) {
            if (brightness[i] >= average) {
                hash |= (1L << i);
            }
        }
        return hash;
    }
}
