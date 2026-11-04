package com.csit314.platform.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/** IDR-15 - uploaded photos get validated for file type and size. */
class FileStorageServiceUnitTests {

    private FileStorageService service(Path tempDir) {
        return new FileStorageService(tempDir.toString());
    }

    @Test
    void acceptsASupportedImageType(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.jpg", "image/jpeg", realPngBytes());

        assertThatCode(() -> service.validate(file)).doesNotThrowAnyException();
    }

    // the JDK's ImageIO has no WEBP reader, so a genuine WEBP file would always fail the
    // decode check - WEBP is excluded from the allowed types instead of being silently broken
    @Test
    void rejectsWebpSinceImageIoCantDecodeIt(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.webp", "image/webp", "x".getBytes());

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supported image type");
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
    void acceptsAFileRightAtTheLimit(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        FileStorageService service = service(tempDir);
        // a real (decodable) image padded out to exactly the size limit - trailing bytes
        // after a PNG's IEND chunk don't stop it decoding
        byte[] realPng = realPngBytes();
        byte[] exactlyFiveMb = new byte[5 * 1024 * 1024];
        System.arraycopy(realPng, 0, exactlyFiveMb, 0, realPng.length);
        MockMultipartFile file = new MockMultipartFile("photos", "max.jpg", "image/jpeg", exactlyFiveMb);

        assertThatCode(() -> service.validate(file)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSvgSinceItCanCarryAScript(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.svg", "image/svg+xml",
                "<svg onload=\"alert(1)\"></svg>".getBytes());

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("supported image type");
    }

    @Test
    void rejectsAFileWhoseBytesDontMatchItsClaimedType(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("valid image");
    }

    // a caller that validates a whole batch up front (ProjectService) needs the decode
    // failure to surface at validate() time, not only once store() is reached - otherwise
    // earlier files in the batch are already written to disk before this one is caught
    @Test
    void validateAloneCatchesBytesThatDontMatchTheClaimedType(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        FileStorageService service = service(tempDir);
        MockMultipartFile file = new MockMultipartFile("photos", "a.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());

        assertThatThrownBy(() -> service.validate(file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("valid image");
    }

    @Test
    void storesARealImageUsingTheExtensionFromTheCheckedTypeNotTheFilename(
            @org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        FileStorageService service = service(tempDir);
        byte[] realPng = realPngBytes();
        // filename claims .exe - the stored extension must come from the checked content type instead
        MockMultipartFile file = new MockMultipartFile("photos", "photo.exe", "image/png", realPng);

        String url = service.store(file);

        assertThat(url).endsWith(".png");
        assertThat(Files.exists(tempDir.resolve("projects").resolve(url.substring(url.lastIndexOf('/') + 1)))).isTrue();
    }

    private byte[] realPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
