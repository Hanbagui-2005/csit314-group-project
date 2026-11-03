package com.csit314.platform.config;

import com.csit314.platform.service.PlatformReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

// PM-12 - "I want reports to generate automatically on a schedule so I don't have to
// run them by hand." Writes the same CSV PM-09's export link produces to a file on disk
// every morning, so a report actually appears without anyone clicking anything - not
// just a log line claiming it ran, which is easy to fake and hard to demo
@Component
public class ReportGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationScheduler.class);

    private final PlatformReportService platformReportService;
    private final Path reportsDir;

    public ReportGenerationScheduler(PlatformReportService platformReportService,
                                     @Value("${app.reports-dir:generated-reports}") String reportsDir) {
        this.platformReportService = platformReportService;
        this.reportsDir = Path.of(reportsDir);
        try {
            Files.createDirectories(this.reportsDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Scheduled(cron = "${app.report-generation.cron:0 0 6 * * *}")
    public void generateDailyReport() {
        generateReportFor(LocalDate.now());
    }

    // pulled out so a scheduler test can call it directly instead of waiting for 6am
    void generateReportFor(LocalDate date) {
        String csv = platformReportService.exportReportsAsCsv();
        Path file = reportsDir.resolve("platform-activity-" + date + ".csv");
        try {
            Files.writeString(file, csv);
            log.info("Generated the daily platform activity report automatically: {}", file);
        } catch (IOException e) {
            log.error("Failed to write the scheduled platform activity report", e);
        }
    }
}
