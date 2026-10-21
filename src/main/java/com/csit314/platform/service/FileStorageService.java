package com.csit314.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

// saves the uploaded photo locally and gives back the url to save on the project
@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir, "projects");
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String store(MultipartFile file) {
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
