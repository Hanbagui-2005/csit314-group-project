package com.csit314.platform.config;

import com.csit314.platform.service.PlatformReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// PM-12
@ExtendWith(MockitoExtension.class)
class ReportGenerationSchedulerUnitTests {

    @Mock PlatformReportService platformReportService;

    @Test
    void writesTheCsvReportToADateStampedFile(@TempDir Path tempDir) throws Exception {
        when(platformReportService.exportReportsAsCsv()).thenReturn("Period,New Customers\nToday,3\n");
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(platformReportService, tempDir.toString());

        scheduler.generateReportFor(LocalDate.of(2026, 11, 3));

        Path expected = tempDir.resolve("platform-activity-2026-11-03.csv");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readString(expected)).isEqualTo("Period,New Customers\nToday,3\n");
    }

    @Test
    void createsTheReportsDirectoryIfItDoesNotExistYet(@TempDir Path tempDir) {
        Path nested = tempDir.resolve("does-not-exist-yet");

        assertThatCode(() -> new ReportGenerationScheduler(platformReportService, nested.toString()))
                .doesNotThrowAnyException();
        assertThat(Files.isDirectory(nested)).isTrue();
    }
}
