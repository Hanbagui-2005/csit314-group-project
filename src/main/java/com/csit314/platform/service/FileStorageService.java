package com.csit314.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// saves the uploaded photo locally and gives back the url to save on the project
@Service
public class FileStorageService {

    // IDR-15
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    // SVG is deliberately excluded - it can carry a <script>, and would be served back
    // from our own origin as stored XSS. The extension always comes from this map, never
    // from the uploaded filename, so a file can't be smuggled in under a different type.
    // WEBP is also excluded - the JDK's ImageIO has no built-in WEBP reader, so the decode
    // check below would reject every genuine WEBP file as invalid.
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif");

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir, "projects");
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // IDR-15 - checked up front by callers before storing a whole batch, so one bad file
    // doesn't leave earlier valid ones written to disk. The decode check lives here (not
    // just in store()) for exactly that reason - a caller that validates every file before
    // storing any of them needs the decode failure to surface at validate() time too.
    public void validate(MultipartFile file) {
        String name = displayName(file);
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new ServiceException(name + " isn't a supported image type. Use JPG, PNG or GIF.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ServiceException(name + " is too large. Photos must be under 5MB each.");
        }
        // the claimed content type can be spoofed, so also decode the bytes as an image -
        // this is what actually blocks an HTML/script file relabelled with an image type
        if (!decodesAsImage(file)) {
            throw new ServiceException(name + " isn't a valid image file.");
        }
    }

    public String store(MultipartFile file) {
        validate(file);
        byte[] bytes;
        try {
            bytes = file.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType().toLowerCase(Locale.ROOT));
        String filename = UUID.randomUUID() + extension;
        try {
            Files.write(uploadDir.resolve(filename), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "/uploads/projects/" + filename;
    }

    private boolean decodesAsImage(MultipartFile file) {
        try {
            return ImageIO.read(file.getInputStream()) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private String displayName(MultipartFile file) {
        return file.getOriginalFilename() == null ? "That file" : "\"" + file.getOriginalFilename() + "\"";
    }
}
