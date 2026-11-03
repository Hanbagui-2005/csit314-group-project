package com.csit314.platform.config;

import com.csit314.platform.service.GeneratedReportService;
import com.csit314.platform.service.PlatformReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

// PM-12 - file I/O itself lives in GeneratedReportService (and its own tests); this
// just checks the scheduler asks for yesterday's report and hands it off correctly
@ExtendWith(MockitoExtension.class)
class ReportGenerationSchedulerUnitTests {

    @Mock PlatformReportService platformReportService;
    @Mock GeneratedReportService generatedReportService;

    @Test
    void writesTheReportForTheGivenDate() {
        when(platformReportService.exportYesterdayReportAsCsv()).thenReturn("Period,New Customers\nYesterday,3\n");
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(platformReportService, generatedReportService);

        scheduler.generateReportFor(LocalDate.of(2026, 11, 3));

        verify(generatedReportService).write(LocalDate.of(2026, 11, 3), "Period,New Customers\nYesterday,3\n");
    }
}
