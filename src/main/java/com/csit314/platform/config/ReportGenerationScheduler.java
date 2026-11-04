package com.csit314.platform.config;

import com.csit314.platform.service.GeneratedReportService;
import com.csit314.platform.service.PlatformReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// PM-12 - "I want reports to generate automatically on a schedule so I don't have to
// run them by hand." Writes yesterday's complete activity report to a file every
// morning via GeneratedReportService, where Platform Management can list and download
// it (PlatformManagerController) instead of needing server access to find the file
@Component
public class ReportGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationScheduler.class);

    private final PlatformReportService platformReportService;
    private final GeneratedReportService generatedReportService;

    public ReportGenerationScheduler(PlatformReportService platformReportService,
                                     GeneratedReportService generatedReportService) {
        this.platformReportService = platformReportService;
        this.generatedReportService = generatedReportService;
    }

    @Scheduled(cron = "${app.report-generation.cron:0 0 6 * * *}")
    public void generateDailyReport() {
        // yesterday, not today: run at 6am, "today" is only 6 hours old so far, and the
        // same calendar-boundary logic as PM-06/08's own reports means a window since
        // midnight today would make the file nearly empty
        generateReportFor(LocalDate.now().minusDays(1));
    }

    // pulled out so a scheduler test can call it directly instead of waiting for 6am.
    // date is the day the report covers and the file is named after - always
    // "yesterday" relative to when the scheduled job actually runs
    //
    // Anthony's review: this used to always build the CSV from "yesterday relative to
    // right now" regardless of what date was passed in, so calling this with any other
    // date (a backfill, a test) silently wrote the wrong numbers into a correctly-named
    // file
    void generateReportFor(LocalDate date) {
        String csv = platformReportService.exportReportAsCsvFor(date);
        generatedReportService.write(date, csv);
        log.info("Generated the daily platform activity report automatically for {}", date);
    }
}
