package com.csit314.platform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

// PM-12
class GeneratedReportServiceUnitTests {

    @Test
    void createsTheReportsDirectoryIfItDoesNotExistYet(@TempDir Path tempDir) {
        Path nested = tempDir.resolve("does-not-exist-yet");

        assertThatCode(() -> new GeneratedReportService(nested.toString())).doesNotThrowAnyException();
        assertThat(Files.isDirectory(nested)).isTrue();
    }

    @Test
    void writesAndReadsBackADateStampedFile(@TempDir Path tempDir) {
        GeneratedReportService service = new GeneratedReportService(tempDir.toString());

        service.write(LocalDate.of(2026, 11, 3), "Period,New Customers\nYesterday,3\n");

        assertThat(Files.exists(tempDir.resolve("platform-activity-2026-11-03.csv"))).isTrue();
        assertThat(service.read("platform-activity-2026-11-03.csv"))
                .contains("Period,New Customers\nYesterday,3\n");
    }

    @Test
    void listsReportsMostRecentFirst(@TempDir Path tempDir) {
        GeneratedReportService service = new GeneratedReportService(tempDir.toString());
        service.write(LocalDate.of(2026, 11, 1), "a");
        service.write(LocalDate.of(2026, 11, 3), "b");
        service.write(LocalDate.of(2026, 11, 2), "c");

        assertThat(service.listReportFilenames()).containsExactly(
                "platform-activity-2026-11-03.csv",
                "platform-activity-2026-11-02.csv",
                "platform-activity-2026-11-01.csv");
    }

    @Test
    void readReturnsEmptyForAMissingFile(@TempDir Path tempDir) {
        GeneratedReportService service = new GeneratedReportService(tempDir.toString());

        assertThat(service.read("platform-activity-2099-01-01.csv")).isEmpty();
    }

    // the filename comes straight from a @PathVariable in the controller - this is what
    // stops it being used to read an arbitrary file off the server
    @Test
    void readRejectsAnythingThatDoesNotLookLikeAGeneratedReportFilename(@TempDir Path tempDir) throws Exception {
        GeneratedReportService service = new GeneratedReportService(tempDir.toString());
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "sensitive");

        assertThat(service.read("../secret.txt")).isEmpty();
        assertThat(service.read("secret.txt")).isEmpty();
        assertThat(service.read("platform-activity-2026-11-03.csv.txt")).isEmpty();
    }

    @Test
    void listIgnoresFilesThatArentGeneratedReports(@TempDir Path tempDir) throws Exception {
        GeneratedReportService service = new GeneratedReportService(tempDir.toString());
        Files.writeString(tempDir.resolve("notes.txt"), "irrelevant");
        service.write(LocalDate.of(2026, 11, 3), "a");

        assertThat(service.listReportFilenames()).containsExactly("platform-activity-2026-11-03.csv");
    }
}
