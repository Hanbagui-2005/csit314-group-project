package com.csit314.platform.config;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.UserManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

// there was previously no repeatable way to put a realistic-looking dataset into a real
// (MySQL) instance - QA's MockDataGenerator only ever ran inside @Transactional tests
// that roll back against H2. This is opt-in (off by default) and reuses the real
// registration/approval services, so demo data goes through the same validation and
// password hashing as a real sign-up
@Component
public class DemoDataImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataImportRunner.class);
    private static final String DEMO_EMAIL_DOMAIN = "@demo.example.test";
    private static final int MAX_COUNT = 2000;

    private final CustomerService customerService;
    private final DesignerService designerService;
    private final UserManagementService userManagementService;
    private final UserAccountRepository userAccountRepository;
    private final boolean importEnabled;
    private final int count;

    public DemoDataImportRunner(CustomerService customerService,
                                DesignerService designerService,
                                UserManagementService userManagementService,
                                UserAccountRepository userAccountRepository,
                                @Value("${app.demo-data.import:false}") boolean importEnabled,
                                @Value("${app.demo-data.count:50}") int count) {
        this.customerService = customerService;
        this.designerService = designerService;
        this.userManagementService = userManagementService;
        this.userAccountRepository = userAccountRepository;
        this.importEnabled = importEnabled;
        this.count = Math.min(Math.max(count, 1), MAX_COUNT);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!importEnabled) {
            return;
        }
        // safe to run more than once - skip rather than duplicating if it already ran
        if (userAccountRepository.existsByEmail("demo.customer01" + DEMO_EMAIL_DOMAIN)) {
            log.info("Demo data already imported, skipping.");
            return;
        }

        int imported = 0;
        for (CustomerRegistrationForm form : customerForms(count)) {
            customerService.register(form);
            imported++;
        }

        List<Long> designerIds = new ArrayList<>();
        for (DesignerRegistrationForm form : designerForms(count)) {
            InteriorDesigner designer = designerService.register(form);
            designerIds.add(designer.getId());
            imported++;
        }
        // registrations start PENDING (IDR-01) - approve them so the demo platform
        // actually has browsable designers and projects right away
        int approved = userManagementService.bulkApproveDesigners(designerIds);

        log.info("Imported {} demo accounts ({} customers, {} designers, {} auto-approved).",
                imported, count, count, approved);
    }

    private List<CustomerRegistrationForm> customerForms(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new CustomerRegistrationForm(
                        "Demo Customer %02d".formatted(i),
                        "demo.customer%02d%s".formatted(i, DEMO_EMAIL_DOMAIN),
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
