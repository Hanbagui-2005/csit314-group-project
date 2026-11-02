package com.csit314.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// saves the uploaded photo locally and gives back the url to save on the project
@Service
public class FileStorageService {

    // IDR-15
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml");

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir, "projects");
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // IDR-15 - checked up front by callers before storing a whole batch, so one bad
    // file doesn't leave earlier valid ones written to disk
    public void validate(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "That file" : "\"" + file.getOriginalFilename() + "\"";
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ServiceException(name + " isn't a supported image type. Use JPG, PNG, WEBP, GIF or SVG.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ServiceException(name + " is too large. Photos must be under 5MB each.");
        }
    }

    public String store(MultipartFile file) {
        validate(file);
        String extension = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + extension;
        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "/uploads/projects/" + filename;
    }
}
