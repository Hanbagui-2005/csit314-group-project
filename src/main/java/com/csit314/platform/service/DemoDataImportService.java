package com.csit314.platform.service;

import com.csit314.platform.dto.CustomerRegistrationForm;
import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    // Anthony's review - every demo account can share one password; these are
    // synthetic placeholder accounts, not real users, so a single memorable password
    // is a genuine usability win for whoever wants to log in and poke around
    private static final String DEMO_PASSWORD = "DemoAccount!2026";
    // flush+clear periodically so a large import doesn't hold thousands of pending
    // entities in the persistence context at once - each JPA write auto-flushes
    // first, and that flush has to dirty-check every one of them
    private static final int BATCH_SIZE = 50;

    private final CustomerService customerService;
    private final DesignerService designerService;
    private final UserManagementService userManagementService;
    private final UserAccountRepository userAccountRepository;
    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;

    public DemoDataImportService(CustomerService customerService,
                                 DesignerService designerService,
                                 UserManagementService userManagementService,
                                 UserAccountRepository userAccountRepository,
                                 EntityManager entityManager,
                                 PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.designerService = designerService;
        this.userManagementService = userManagementService;
        this.userAccountRepository = userAccountRepository;
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
    }

    // Anthony's review, two rounds:
    // - this used to run as a loop of separate transactions (one per registration),
    //   each committing on its own. If anything failed partway through, whatever had
    //   already been saved stayed committed, and the "already imported" check only
    //   ever looked at the first customer - so a later run just saw that one record
    //   and silently skipped, with no way to finish or recover. It's one transaction
    //   now: either everything lands, or nothing does
    // - the "already imported" check itself was also too coarse: it skipped the
    //   *entire* run if the first customer already existed, so re-running with a
    //   bigger count never created the extra accounts. Each account is now checked
    //   (and skipped) individually instead, so a bigger re-run fills in the rest
    @Transactional
    public DemoDataImportResult importDemoData(int count) {
        // Anthony's review - every account below shares DEMO_PASSWORD, so it only
        // needs to be BCrypt-hashed once here instead of once per registration; with
        // a large count that was several minutes of hashing the exact same string
        String encodedDemoPassword = passwordEncoder.encode(DEMO_PASSWORD);

        int customersImported = 0;
        int processed = 0;
        for (CustomerRegistrationForm form : customerForms(count)) {
            if (userAccountRepository.existsByEmail(form.email())) {
                continue;
            }
            customerService.registerWithEncodedPassword(form, encodedDemoPassword);
            customersImported++;
            processed = flushPeriodically(++processed);
        }

        int designersImported = 0;
        List<Long> designerIds = new ArrayList<>();
        for (DesignerRegistrationForm form : designerForms(count)) {
            if (userAccountRepository.existsByEmail(form.email())) {
                continue;
            }
            InteriorDesigner designer = designerService.registerWithEncodedPassword(form, encodedDemoPassword);
            designerIds.add(designer.getId());
            designersImported++;
            processed = flushPeriodically(++processed);
        }
        // registrations start PENDING (IDR-01) - approve them so the demo platform
        // actually has browsable designers and projects right away. Newly-registered
        // designers only, not ones that already existed from an earlier run and may
        // have since been rejected/suspended on purpose
        int approved = userManagementService.bulkApproveDesigners(designerIds);

        return new DemoDataImportResult(customersImported, designersImported, approved);
    }

    private int flushPeriodically(int processedSoFar) {
        if (processedSoFar % BATCH_SIZE == 0) {
            entityManager.flush();
            entityManager.clear();
        }
        return processedSoFar;
    }

    private List<CustomerRegistrationForm> customerForms(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new CustomerRegistrationForm(
                        "Demo Customer %02d".formatted(i),
                        "demo.customer%02d%s".formatted(i, DEMO_EMAIL_DOMAIN),
                        DEMO_PASSWORD,
                        DEMO_PASSWORD))
                .toList();
    }

    private List<DesignerRegistrationForm> designerForms(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new DesignerRegistrationForm(
                        "Demo Designer %02d".formatted(i),
                        "demo.designer%02d%s".formatted(i, DEMO_EMAIL_DOMAIN),
                        DEMO_PASSWORD,
                        DEMO_PASSWORD,
                        "Demo Studio %02d".formatted(i),
                        "A synthetic demo studio for showcasing the platform.",
                        "555%07d".formatted(i),
                        null,
                        "Demo Region %02d".formatted(i)))
                .toList();
    }
}
