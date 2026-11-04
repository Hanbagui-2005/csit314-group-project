package com.csit314.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// PM-12 - file storage for the scheduler's generated reports, kept separate from
// ReportGenerationScheduler so Platform Management can list and download them through
// the UI (PlatformManagerController) instead of needing server access to find the file
@Service
public class GeneratedReportService {

    // only this exact shape is ever accepted back as a filename to read - rules out
    // path traversal (e.g. "../../etc/passwd") entirely, since nothing matching this
    // pattern can contain a path separator
    private static final Pattern FILENAME_PATTERN = Pattern.compile("platform-activity-\\d{4}-\\d{2}-\\d{2}\\.csv");

    private final Path reportsDir;

    public GeneratedReportService(@Value("${app.reports-dir:generated-reports}") String reportsDir) {
        this.reportsDir = Path.of(reportsDir);
        try {
            Files.createDirectories(this.reportsDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(LocalDate date, String csv) {
        try {
            Files.writeString(reportsDir.resolve("platform-activity-" + date + ".csv"), csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // most recent first, so Platform Management doesn't have to scroll to find today's
    public List<String> listReportFilenames() {
        try (var stream = Files.list(reportsDir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> FILENAME_PATTERN.matcher(name).matches())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String> read(String filename) {
        if (!FILENAME_PATTERN.matcher(filename).matches()) {
            return Optional.empty();
        }
        Path file = reportsDir.resolve(filename);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
