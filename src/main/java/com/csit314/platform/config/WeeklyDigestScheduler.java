package com.csit314.platform.config;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.service.WeeklySummaryService;
import com.csit314.platform.web.dto.WeeklySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// IDR-08 - no mail server yet (same as password reset), so the weekly digest goes to
// the log for now instead of an actual email. Runs every Monday 8am; the data behind
// it is also available any time on /designer/weekly-summary, no need to wait for this
@Component
public class WeeklyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestScheduler.class);

    private final InteriorDesignerRepository designerRepository;
    private final WeeklySummaryService weeklySummaryService;

    public WeeklyDigestScheduler(InteriorDesignerRepository designerRepository,
                                 WeeklySummaryService weeklySummaryService) {
        this.designerRepository = designerRepository;
        this.weeklySummaryService = weeklySummaryService;
    }

    @Scheduled(cron = "${app.weekly-digest.cron:0 0 8 * * MON}")
    public void sendWeeklyDigests() {
        for (InteriorDesigner designer : designerRepository.findByStatus(AccountStatus.ACTIVE)) {
            WeeklySummary summary = weeklySummaryService.summarise(designer);
            log.info("Weekly digest for {}: {} views, {} shortlists, {} messages this week "
                            + "(portfolio totals: {} projects, {} views, {} shortlists)",
                    designer.getEmail(), summary.viewsThisWeek(), summary.shortlistsThisWeek(),
                    summary.messagesThisWeek(), summary.totalProjects(), summary.totalViewsAllTime(),
                    summary.totalShortlistsAllTime());
        }
    }
}
