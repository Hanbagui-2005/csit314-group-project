package com.csit314.platform.service;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// PR #25 review - the repeatable demo-data importer for a real (MySQL) instance
@ExtendWith(MockitoExtension.class)
class DemoDataImportServiceUnitTests {

    @Mock CustomerService customerService;
    @Mock DesignerService designerService;
    @Mock UserManagementService userManagementService;
    @Mock UserAccountRepository userAccountRepository;
    @Mock EntityManager entityManager;
    DemoDataImportService service;

    @BeforeEach
    void setUp() {
        service = new DemoDataImportService(customerService, designerService, userManagementService,
                userAccountRepository, entityManager);
    }

    private InteriorDesigner designerWithId(long id) {
        Company company = new Company("Studio", "Description", "555");
        InteriorDesigner designer = new InteriorDesigner("Contact", "designer" + id + "@example.test",
                "encoded", company);
        ReflectionTestUtils.setField(designer, "id", id);
        return designer;
    }

    @Test
    void importsTheRequestedNumberOfCustomersAndDesignersAndApprovesThem() {
        when(userAccountRepository.existsByEmail(anyString())).thenReturn(false);
        AtomicLong nextId = new AtomicLong(1);
        when(designerService.register(any(DesignerRegistrationForm.class)))
                .thenAnswer(invocation -> designerWithId(nextId.getAndIncrement()));
        when(userManagementService.bulkApproveDesigners(any())).thenReturn(2);

        DemoDataImportResult result = service.importDemoData(2);

        assertThat(result.customersImported()).isEqualTo(2);
        assertThat(result.designersImported()).isEqualTo(2);
        assertThat(result.designersApproved()).isEqualTo(2);
        verify(customerService, times(2)).register(any(CustomerRegistrationForm.class));
        verify(designerService, times(2)).register(any(DesignerRegistrationForm.class));
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userManagementService).bulkApproveDesigners(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L);
    }

    // Anthony's review - the old check looked only at the first customer and skipped
    // the whole run if it existed, so a bigger re-run never created the extra
    // accounts. Each account is checked individually now, so a partial/earlier import
    // just gets filled in rather than skipped or duplicated
    @Test
    void skipsOnlyTheIndividualAccountsThatAlreadyExist() {
        // customer01/designer01 "already exist", customer02/designer02 are new
        when(userAccountRepository.existsByEmail("demo.customer01@demo.example.test")).thenReturn(true);
        when(userAccountRepository.existsByEmail("demo.customer02@demo.example.test")).thenReturn(false);
        when(userAccountRepository.existsByEmail("demo.designer01@demo.example.test")).thenReturn(true);
        when(userAccountRepository.existsByEmail("demo.designer02@demo.example.test")).thenReturn(false);
        when(designerService.register(any(DesignerRegistrationForm.class))).thenReturn(designerWithId(1));

        DemoDataImportResult result = service.importDemoData(2);

        assertThat(result.customersImported()).isEqualTo(1);
        assertThat(result.designersImported()).isEqualTo(1);
        verify(customerService, times(1)).register(any(CustomerRegistrationForm.class));
        verify(designerService, times(1)).register(any(DesignerRegistrationForm.class));
    }

    // Anthony's review - this is the actual fix for "a failure partway through
    // becomes permanent": everything in one transaction means a failure anywhere
    // rolls the whole import back, so the next run starts from a clean slate instead
    // of a half-imported one the per-email skip could otherwise paper over incorrectly
    @Test
    void importRunsAsOneTransactionalMethod() throws NoSuchMethodException {
        var method = DemoDataImportService.class.getMethod("importDemoData", int.class);
        assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("the whole import needs to be one transaction, so a failure partway through "
                        + "rolls everything back instead of leaving a partial import behind")
                .isTrue();
    }

    // Anthony's review - large imports held thousands of pending entities in the
    // persistence context at once, and every subsequent query auto-flushed (and so
    // dirty-checked) all of them first. Flushing and clearing periodically bounds that
    @Test
    void flushesAndClearsThePersistenceContextPeriodically() {
        when(userAccountRepository.existsByEmail(anyString())).thenReturn(false);
        when(designerService.register(any(DesignerRegistrationForm.class))).thenReturn(designerWithId(1));

        service.importDemoData(60);

        // 60 customers + 60 designers = 120 registrations, flushed every 50
        verify(entityManager, atLeast(2)).flush();
        verify(entityManager, atLeast(2)).clear();
    }
}
