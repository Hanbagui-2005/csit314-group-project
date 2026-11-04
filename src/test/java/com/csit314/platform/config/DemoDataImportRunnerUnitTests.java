package com.csit314.platform.config;

import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.service.DemoDataImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

// startup glue only now (PR #25 review) - the actual import logic lives in
// DemoDataImportService, covered separately in DemoDataImportServiceUnitTests
@ExtendWith(MockitoExtension.class)
class DemoDataImportRunnerUnitTests {

    @Mock DemoDataImportService demoDataImportService;

    @Test
    void doesNothingWhenImportIsNotEnabled() {
        DemoDataImportRunner runner = new DemoDataImportRunner(demoDataImportService, false, 2);

        runner.run(null);

        verifyNoInteractions(demoDataImportService);
    }

    @Test
    void delegatesToTheServiceWithTheConfiguredCount() {
        when(demoDataImportService.importDemoData(2)).thenReturn(new DemoDataImportResult(2, 2, 2));
        DemoDataImportRunner runner = new DemoDataImportRunner(demoDataImportService, true, 2);

        runner.run(null);

        verify(demoDataImportService).importDemoData(2);
    }

    @Test
    void countIsClampedToAtLeastOneAndAtMostTheMaximum() {
        when(demoDataImportService.importDemoData(anyInt())).thenReturn(new DemoDataImportResult(1, 1, 1));
        DemoDataImportRunner tooLow = new DemoDataImportRunner(demoDataImportService, true, 0);

        tooLow.run(null);

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(demoDataImportService).importDemoData(countCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(countCaptor.getValue()).isEqualTo(1);
    }

    // Anthony's review - a failed demo import (e.g. a leftover half-imported database
    // from an interrupted previous run) must not fail the whole application context.
    // This is optional, opt-in tooling - it should log and let the app start anyway
    @Test
    void catchesAFailedImportInsteadOfLettingItFailApplicationStartup() {
        when(demoDataImportService.importDemoData(2)).thenThrow(new RuntimeException("boom"));
        DemoDataImportRunner runner = new DemoDataImportRunner(demoDataImportService, true, 2);

        org.assertj.core.api.Assertions.assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }
}
