package com.csit314.platform.config;

import com.csit314.platform.model.Project;
import com.csit314.platform.repository.PhotoHashRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.service.ImageSimilarityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// PM-04 - photo_hashes starts empty, so without this, every photo already on the
// platform when this feature shipped (including all the seed/demo data) is invisible to
// the plagiarism check: copying one of them is only ever caught if the ORIGINAL was
// uploaded after this change, not the copy. Runs on every startup, but existsByPhotoUrl
// means only genuinely new (never-hashed) photos do any work each time
@Component
public class PhotoHashBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PhotoHashBackfillRunner.class);

    private final ProjectRepository projectRepository;
    private final PhotoHashRepository photoHashRepository;
    private final ImageSimilarityService imageSimilarityService;
    private final Path uploadDir;

    public PhotoHashBackfillRunner(ProjectRepository projectRepository, PhotoHashRepository photoHashRepository,
                                   ImageSimilarityService imageSimilarityService,
                                   @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.projectRepository = projectRepository;
        this.photoHashRepository = photoHashRepository;
        this.imageSimilarityService = imageSimilarityService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int hashed = 0;
        for (Project project : projectRepository.findAll()) {
            hashed += backfill(project, project.getPhotoUrls());
            hashed += backfill(project, project.getBeforePhotoUrls());
        }
        if (hashed > 0) {
            log.info("Backfilled perceptual hashes for {} pre-existing project photo(s)", hashed);
        }
    }

    private int backfill(Project project, List<String> photoUrls) {
        int hashed = 0;
        for (String photoUrl : photoUrls) {
            if (photoHashRepository.existsByPhotoUrl(photoUrl)) {
                continue;
            }
            Path file = resolve(photoUrl);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                imageSimilarityService.recordOnly(project, photoUrl, Files.readAllBytes(file));
                hashed++;
            } catch (IOException e) {
                log.warn("Couldn't read {} while backfilling photo hashes", file, e);
            }
        }
        return hashed;
    }

    // photoUrl looks like "/uploads/projects/xyz.jpg"; FileStorageService writes the
    // actual file to {app.upload-dir}/projects/xyz.jpg, so strip the leading "/uploads/"
    private Path resolve(String photoUrl) {
        String relative = photoUrl.startsWith("/uploads/") ? photoUrl.substring("/uploads/".length()) : photoUrl;
        return uploadDir.resolve(relative);
    }
}
