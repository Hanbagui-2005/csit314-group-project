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
        when(demoDataImportService.importDemoData(2)).thenReturn(new DemoDataImportResult(false, 2, 2, 2));
        DemoDataImportRunner runner = new DemoDataImportRunner(demoDataImportService, true, 2);

        runner.run(null);

        verify(demoDataImportService).importDemoData(2);
    }

    @Test
    void countIsClampedToAtLeastOneAndAtMostTheMaximum() {
        when(demoDataImportService.importDemoData(anyInt())).thenReturn(new DemoDataImportResult(false, 1, 1, 1));
        DemoDataImportRunner tooLow = new DemoDataImportRunner(demoDataImportService, true, 0);

        tooLow.run(null);

        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(demoDataImportService).importDemoData(countCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(countCaptor.getValue()).isEqualTo(1);
    }
}
