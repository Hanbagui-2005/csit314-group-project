package com.csit314.platform.config;

import com.csit314.platform.service.CategoryManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// PM-03 - runs the near-duplicate category merge on a schedule, same as
// WeeklyDigestScheduler, so categories that fragment through any path (not just new
// ones added through addCategory) eventually get cleaned up without anyone having to
// trigger it by hand. Runs daily at 3am, a quiet time with no real users around
@Component
public class CategoryDuplicateMergeScheduler {

    private static final Logger log = LoggerFactory.getLogger(CategoryDuplicateMergeScheduler.class);

    private final CategoryManagementService categoryManagementService;

    public CategoryDuplicateMergeScheduler(CategoryManagementService categoryManagementService) {
        this.categoryManagementService = categoryManagementService;
    }

    @Scheduled(cron = "${app.category-merge.cron:0 0 3 * * *}")
    public void mergeDuplicateCategories() {
        log.info("Running the scheduled near-duplicate category merge");
        categoryManagementService.mergeDuplicateCategories();
    }
}
