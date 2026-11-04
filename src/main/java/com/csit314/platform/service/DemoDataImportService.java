package com.csit314.platform.service;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

// there was previously no repeatable way to put a realistic-looking dataset into a real
// (MySQL) instance - QA's MockDataGenerator only ever ran inside @Transactional tests
// that roll back against H2. This reuses the real registration/approval services, so
// demo data goes through the same validation and password hashing as a real sign-up
@Service
public class DemoDataImportService {

    private static final String DEMO_EMAIL_DOMAIN = "@demo.example.test";

    private final CustomerService customerService;
    private final DesignerService designerService;
    private final UserManagementService userManagementService;
    private final UserAccountRepository userAccountRepository;

    public DemoDataImportService(CustomerService customerService,
                                 DesignerService designerService,
                                 UserManagementService userManagementService,
                                 UserAccountRepository userAccountRepository) {
        this.customerService = customerService;
        this.designerService = designerService;
        this.userManagementService = userManagementService;
        this.userAccountRepository = userAccountRepository;
    }

    // Anthony's review - this used to run as a loop of separate transactions (one per
    // registration), each committing on its own. If anything failed partway through (a
    // duplicate email, a DB error, the process getting killed), whatever had already
    // been saved stayed committed, and the "already imported" check only ever looked
    // at the first customer - so every later run just saw that one record and silently
    // skipped, with no way to finish or recover. The whole import is now one
    // transaction instead: either everything lands, or nothing does
    @Transactional
    public DemoDataImportResult importDemoData(int count) {
        if (userAccountRepository.existsByEmail(demoCustomerEmail(1))) {
            return DemoDataImportResult.skipped();
        }

        for (CustomerRegistrationForm form : customerForms(count)) {
            customerService.register(form);
        }

        List<Long> designerIds = new ArrayList<>();
        for (DesignerRegistrationForm form : designerForms(count)) {
            InteriorDesigner designer = designerService.register(form);
            designerIds.add(designer.getId());
        }
        // registrations start PENDING (IDR-01) - approve them so the demo platform
        // actually has browsable designers and projects right away
        int approved = userManagementService.bulkApproveDesigners(designerIds);

        return new DemoDataImportResult(false, count, count, approved);
    }

    private String demoCustomerEmail(int i) {
        return "demo.customer%02d%s".formatted(i, DEMO_EMAIL_DOMAIN);
    }

    private List<CustomerRegistrationForm> customerForms(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new CustomerRegistrationForm(
                        "Demo Customer %02d".formatted(i),
                        demoCustomerEmail(i),
                        "DemoPass!%04d".formatted(i),
                        "DemoPass!%04d".formatted(i)))
                .toList();
    }

    private List<DesignerRegistrationForm> designerForms(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new DesignerRegistrationForm(
                        "Demo Designer %02d".formatted(i),
                        "demo.designer%02d%s".formatted(i, DEMO_EMAIL_DOMAIN),
                        "DemoPass!%04d".formatted(i),
                        "DemoPass!%04d".formatted(i),
                        "Demo Studio %02d".formatted(i),
                        "A synthetic demo studio for showcasing the platform.",
                        "555%07d".formatted(i),
                        null,
                        "Demo Region %02d".formatted(i)))
                .toList();
    }
}
