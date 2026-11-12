package com.csit314.platform.web;

import com.csit314.platform.dto.CategoryAddResult;
import com.csit314.platform.dto.CategoryViewRanking;
import com.csit314.platform.dto.MonthlyGrowth;
import com.csit314.platform.service.CategoryManagementService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.GeneratedReportService;
import com.csit314.platform.service.PlatformReportService;
import com.csit314.platform.service.ProjectFlagService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.service.SystemErrorLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/platform-manager")
public class PlatformManagerController {

    private final CategoryManagementService categoryManagementService;
    private final PlatformReportService platformReportService;
    private final DesignerService designerService;
    private final ProjectFlagService projectFlagService;
    private final SystemErrorLogService systemErrorLogService;
    private final GeneratedReportService generatedReportService;

    public PlatformManagerController(CategoryManagementService categoryManagementService,
                                     PlatformReportService platformReportService,
                                     DesignerService designerService,
                                     ProjectFlagService projectFlagService,
                                     SystemErrorLogService systemErrorLogService,
                                     GeneratedReportService generatedReportService) {
        this.categoryManagementService = categoryManagementService;
        this.platformReportService = platformReportService;
        this.designerService = designerService;
        this.projectFlagService = projectFlagService;
        this.systemErrorLogService = systemErrorLogService;
        this.generatedReportService = generatedReportService;
    }

    // PM-01
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categoryManagementService.listCategories());
        return "platform-manager-categories";
    }

    // PM-01/PM-03
    @PostMapping("/categories")
    public String addCategory(@RequestParam(required = false) String name, RedirectAttributes redirect) {
        try {
            // Anthony's review - the service says explicitly whether this merged into
            // a near-duplicate, instead of the controller guessing from spelling
            // (which got a case-insensitive exact-match reactivation wrong)
            CategoryAddResult result = categoryManagementService.addCategory(name);
            redirect.addFlashAttribute("successMessage", result.mergedIntoNearDuplicate()
                    ? "That's close to an existing category (\"" + result.category().getName()
                            + "\") - using it instead of creating a near-duplicate."
                    : "Category added.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/categories";
    }

    // category management - supports PM-01; rename/delete aren't their own tracked
    // Taiga stories (PM-03 is automatic duplicate-merging, not manual rename)
    @PostMapping("/categories/{id}/rename")
    public String renameCategory(@PathVariable Long id, @RequestParam(required = false) String name,
                                 RedirectAttributes redirect) {
        try {
            categoryManagementService.renameCategory(id, name);
            redirect.addFlashAttribute("successMessage", "Category renamed.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/categories";
    }

    // PM-13 - soft delete; a scheduled job also runs this on a cron, but it's exposed
    // here too so it doesn't depend on waiting for 3am to see it work
    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            categoryManagementService.deleteCategory(id);
            redirect.addFlashAttribute("successMessage", "Category deactivated.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/categories";
    }

    // PM-03 - CategoryDuplicateMergeScheduler already runs this nightly; this button is
    // just so the merge is demonstrable on demand instead of waiting for the cron
    @PostMapping("/categories/merge-duplicates")
    public String mergeDuplicateCategories(RedirectAttributes redirect) {
        categoryManagementService.mergeDuplicateCategories();
        redirect.addFlashAttribute("successMessage", "Checked for near-duplicate categories and merged any found.");
        return "redirect:/platform-manager/categories";
    }

    // PM-06 (daily figures) / PM-08 (monthly summary) combined into one report with a
    // bonus weekly window. PM-02 (growth forecast), PM-10 (top performers) and PM-14
    // (anomaly alerts) all surface here too since they're all report-page content
    @GetMapping("/reports")
    public String reports(Model model) {
        var categoryCoverage = platformReportService.categoryDesignerCoverage();
        var categoryViewRanking = platformReportService.categoryViewRanking();
        var growthForecast = platformReportService.growthForecast();

        model.addAttribute("reports", platformReportService.reports());
        model.addAttribute("categoryCoverage", categoryCoverage);
        model.addAttribute("categoryViewRanking", categoryViewRanking);
        model.addAttribute("growthForecast", growthForecast);
        model.addAttribute("topDesigners", platformReportService.topDesigners());
        model.addAttribute("topProjects", platformReportService.topProjects());
        model.addAttribute("anomalyAlerts", platformReportService.anomalyAlerts());
        // PM-12 - so the scheduled job's output is reachable from the UI, not just the
        // server's local disk
        model.addAttribute("generatedReports", generatedReportService.listReportFilenames());

        // scaling denominators for the bar charts on the reports page - view-only
        // arithmetic, so it lives here rather than in PlatformReportService
        model.addAttribute("maxCoverageCount", categoryCoverage.stream()
                .flatMapToLong(c -> java.util.stream.LongStream.of(c.activeDesignerCount(), c.demandCount()))
                .max().orElse(1));
        model.addAttribute("maxCategoryViews", categoryViewRanking.stream()
                .mapToLong(CategoryViewRanking::totalViews).max().orElse(1));
        model.addAttribute("maxGrowthCount", java.util.stream.Stream.concat(
                        growthForecast.history().stream().mapToLong(MonthlyGrowth::newUsers).boxed(),
                        java.util.stream.Stream.of(growthForecast.projectedNewUsers()))
                .mapToLong(Long::longValue).max().orElse(1));

        return "platform-manager-reports";
    }

    // PM-09 - CSV export of the activity report (the "or CSV" half of "PDF or CSV" -
    // see PlatformReportService.exportReportsAsCsv for why PDF isn't implemented)
    @GetMapping("/reports/export.csv")
    public ResponseEntity<String> exportReportsAsCsv() {
        String csv = platformReportService.exportReportsAsCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"platform-activity-report.csv\"")
                .body(csv);
    }

    // PM-12 - downloads one of the scheduler's previously-generated reports; the
    // filename is validated by GeneratedReportService itself before it ever touches the
    // filesystem, so this can't be used to read an arbitrary file off the server
    @GetMapping("/reports/generated/{filename}")
    public ResponseEntity<String> downloadGeneratedReport(@PathVariable String filename) {
        return generatedReportService.read(filename)
                .map(csv -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .body(csv))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PM-11
    @GetMapping("/flagged-projects")
    public String flaggedProjects(Model model) {
        model.addAttribute("flags", projectFlagService.all());
        return "platform-manager-flagged-projects";
    }

    // PM-11
    @PostMapping("/flagged-projects/{id}/review")
    public String reviewFlaggedProject(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            projectFlagService.review(id);
            redirect.addFlashAttribute("successMessage", "Marked as reviewed.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/flagged-projects";
    }

    // PM-11
    @PostMapping("/flagged-projects/{id}/dismiss")
    public String dismissFlaggedProject(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            projectFlagService.dismiss(id);
            redirect.addFlashAttribute("successMessage", "Flag dismissed.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/flagged-projects";
    }

    // PM-15
    @GetMapping("/error-log")
    public String errorLog(Model model) {
        model.addAttribute("errors", systemErrorLogService.recent());
        return "platform-manager-error-log";
    }

    // PM-05
    @GetMapping("/designers")
    public String designers(Model model) {
        model.addAttribute("designers", designerService.listAll());
        return "platform-manager-designers";
    }

    // PM-05
    @PostMapping("/designers/{id}/verify-service-area")
    public String verifyServiceArea(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            designerService.setServiceAreaVerified(id, true);
            redirect.addFlashAttribute("successMessage", "Service area marked as verified.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/designers";
    }

    // PM-05
    @PostMapping("/designers/{id}/flag-service-area")
    public String flagServiceArea(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            designerService.setServiceAreaVerified(id, false);
            redirect.addFlashAttribute("successMessage", "Service area flagged as inaccurate.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/designers";
    }
}
