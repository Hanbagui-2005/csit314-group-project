package com.csit314.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/** IDR-15 - uploaded photos get validated for file type and size. */
class FileStorageServiceUnitTests {

    private FileStorageService service(Path tempDir) {
        return new FileStorageService(tempDir.toString());
    }

    @Test
    void acceptsASupportedImageType(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.jpg", "image/jpeg", "x".getBytes());

        assertThatCode(() -> service.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnsupportedContentType(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supported image type");
    }

    @Test
    void rejectsAMissingContentType(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.jpg", null, "x".getBytes());

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supported image type");
    }

    @Test
    void rejectsAFileOverFiveMegabytes(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        byte[] tooBig = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("photos", "big.jpg", "image/jpeg", tooBig);

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void acceptsAFileRightAtTheLimit(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        byte[] exactlyFiveMb = new byte[5 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("photos", "max.jpg", "image/jpeg", exactlyFiveMb);

        assertThatCode(() -> service.validate(file)).doesNotThrowAnyException();
    }
}
