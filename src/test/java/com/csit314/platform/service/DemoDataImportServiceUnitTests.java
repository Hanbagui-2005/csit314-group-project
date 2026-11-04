package com.csit314.platform.service;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
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

// PR #25 review - the repeatable demo-data importer for a real (MySQL) instance
@ExtendWith(MockitoExtension.class)
class DemoDataImportServiceUnitTests {

    @Mock CustomerService customerService;
    @Mock DesignerService designerService;
    @Mock UserManagementService userManagementService;
    @Mock UserAccountRepository userAccountRepository;
    DemoDataImportService service;

    @BeforeEach
    void setUp() {
        service = new DemoDataImportService(customerService, designerService, userManagementService,
                userAccountRepository);
    }

    private InteriorDesigner designerWithId(long id) {
        Company company = new Company("Studio", "Description", "555");
        InteriorDesigner designer = new InteriorDesigner("Contact", "designer" + id + "@example.test",
                "encoded", company);
        ReflectionTestUtils.setField(designer, "id", id);
        return designer;
    }

    @Test
    void skipsWhenDemoDataWasAlreadyImported() {
        when(userAccountRepository.existsByEmail("demo.customer01@demo.example.test")).thenReturn(true);

        DemoDataImportResult result = service.importDemoData(2);

        assertThat(result.alreadyImported()).isTrue();
        verifyNoInteractions(customerService, designerService, userManagementService);
    }

    @Test
    void importsTheRequestedNumberOfCustomersAndDesignersAndApprovesThem() {
        when(userAccountRepository.existsByEmail("demo.customer01@demo.example.test")).thenReturn(false);
        AtomicLong nextId = new AtomicLong(1);
        when(designerService.register(any(DesignerRegistrationForm.class)))
                .thenAnswer(invocation -> designerWithId(nextId.getAndIncrement()));
        when(userManagementService.bulkApproveDesigners(any())).thenReturn(2);

        DemoDataImportResult result = service.importDemoData(2);

        assertThat(result.alreadyImported()).isFalse();
        assertThat(result.customersImported()).isEqualTo(2);
        assertThat(result.designersImported()).isEqualTo(2);
        assertThat(result.designersApproved()).isEqualTo(2);
        verify(customerService, times(2)).register(any(CustomerRegistrationForm.class));
        verify(designerService, times(2)).register(any(DesignerRegistrationForm.class));
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userManagementService).bulkApproveDesigners(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void importRunsAsOneTransactionalMethod() throws NoSuchMethodException {
        var method = DemoDataImportService.class.getMethod("importDemoData", int.class);
        assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("the whole import needs to be one transaction, so a failure partway through "
                        + "rolls everything back instead of leaving a partial import behind")
                .isTrue();
    }
}
