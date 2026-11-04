package com.csit314.platform.config;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// the repeatable demo-data importer for a real (MySQL) instance - off by default
@ExtendWith(MockitoExtension.class)
class DemoDataImportRunnerUnitTests {

    @Mock CustomerService customerService;
    @Mock DesignerService designerService;
    @Mock UserManagementService userManagementService;
    @Mock UserAccountRepository userAccountRepository;

    private InteriorDesigner designerWithId(long id) {
        Company company = new Company("Studio", "Description", "555");
        InteriorDesigner designer = new InteriorDesigner("Contact", "designer" + id + "@example.test",
                "encoded", company);
        ReflectionTestUtils.setField(designer, "id", id);
        return designer;
    }

    @Test
    void doesNothingWhenImportIsNotEnabled() {
        DemoDataImportRunner runner = new DemoDataImportRunner(
                customerService, designerService, userManagementService, userAccountRepository,
                false, 2);

        runner.run(null);

        verifyNoInteractions(customerService, designerService, userManagementService);
    }

    @Test
    void skipsWhenDemoDataWasAlreadyImported() {
        when(userAccountRepository.existsByEmail("demo.customer01@demo.example.test")).thenReturn(true);

        DemoDataImportRunner runner = new DemoDataImportRunner(
                customerService, designerService, userManagementService, userAccountRepository,
                true, 2);
        runner.run(null);

        verifyNoInteractions(customerService, designerService, userManagementService);
    }

    @Test
    void importsTheRequestedNumberOfCustomersAndDesignersAndApprovesThem() {
        when(userAccountRepository.existsByEmail("demo.customer01@demo.example.test")).thenReturn(false);
        AtomicLong nextId = new AtomicLong(1);
        when(designerService.register(any(DesignerRegistrationForm.class)))
                .thenAnswer(invocation -> designerWithId(nextId.getAndIncrement()));

        DemoDataImportRunner runner = new DemoDataImportRunner(
                customerService, designerService, userManagementService, userAccountRepository,
                true, 2);
        runner.run(null);

        verify(customerService, times(2)).register(any(CustomerRegistrationForm.class));
        verify(designerService, times(2)).register(any(DesignerRegistrationForm.class));
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userManagementService).bulkApproveDesigners(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void countIsClampedToAtLeastOneAndAtMostTheMaximum() {
        when(userAccountRepository.existsByEmail(any())).thenReturn(false);
        when(designerService.register(any(DesignerRegistrationForm.class))).thenReturn(designerWithId(1));

        DemoDataImportRunner tooLow = new DemoDataImportRunner(
                customerService, designerService, userManagementService, userAccountRepository,
                true, 0);
        tooLow.run(null);

        verify(customerService, times(1)).register(any(CustomerRegistrationForm.class));
    }
}
