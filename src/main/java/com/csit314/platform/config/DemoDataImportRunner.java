package com.csit314.platform.config;

import com.csit314.platform.dto.DemoDataImportResult;
import com.csit314.platform.service.DemoDataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// opt-in (off by default) - startup glue only. The actual import is one transaction in
// DemoDataImportService, not here, so a failure partway through rolls back cleanly
@Component
public class DemoDataImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataImportRunner.class);
    private static final int MAX_COUNT = 2000;

    private final DemoDataImportService demoDataImportService;
    private final boolean importEnabled;
    private final int count;

    public DemoDataImportRunner(DemoDataImportService demoDataImportService,
                                @Value("${app.demo-data.import:false}") boolean importEnabled,
                                @Value("${app.demo-data.count:50}") int count) {
        this.demoDataImportService = demoDataImportService;
        this.importEnabled = importEnabled;
        this.count = Math.min(Math.max(count, 1), MAX_COUNT);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!importEnabled) {
            return;
        }
        // Anthony's review - this is optional, opt-in demo data. An unhandled
        // exception from an ApplicationRunner fails the whole application context, so
        // without this, leaving app.demo-data.import=true set on a long-running
        // instance would mean every restart fails until someone notices and removes it
        try {
            DemoDataImportResult result = demoDataImportService.importDemoData(count);
            log.info("Demo data import finished: {} new customers, {} new designers ({} auto-approved). "
                            + "Accounts that already existed were left as-is.",
                    result.customersImported(), result.designersImported(), result.designersApproved());
        } catch (Exception e) {
            log.error("Demo data import failed - continuing to start without it. Fix the underlying issue "
                    + "and restart, or remove app.demo-data.import to stop retrying it.", e);
        }
    }
}
