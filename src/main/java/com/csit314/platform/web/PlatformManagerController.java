package com.csit314.platform.web;

import com.csit314.platform.model.Category;
import com.csit314.platform.service.CategoryManagementService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.PlatformReportService;
import com.csit314.platform.service.ProjectFlagService;
import com.csit314.platform.service.ServiceException;
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

    public PlatformManagerController(CategoryManagementService categoryManagementService,
                                     PlatformReportService platformReportService,
                                     DesignerService designerService,
                                     ProjectFlagService projectFlagService) {
        this.categoryManagementService = categoryManagementService;
        this.platformReportService = platformReportService;
        this.designerService = designerService;
        this.projectFlagService = projectFlagService;
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
            Category result = categoryManagementService.addCategory(name);
            // a merged near-duplicate comes back with the existing category's own
            // spelling, not the one just typed in - that's how the controller tells
            // the two cases apart without the service needing its own result type
            boolean merged = name != null && !result.getName().equals(name.trim());
            redirect.addFlashAttribute("successMessage", merged
                    ? "That's close to an existing category (\"" + result.getName() + "\") - using it instead of creating a near-duplicate."
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
        model.addAttribute("reports", platformReportService.reports());
        model.addAttribute("categoryCoverage", platformReportService.categoryDesignerCoverage());
        model.addAttribute("categoryViewRanking", platformReportService.categoryViewRanking());
        model.addAttribute("growthForecast", platformReportService.growthForecast());
        model.addAttribute("topDesigners", platformReportService.topDesigners());
        model.addAttribute("topProjects", platformReportService.topProjects());
        model.addAttribute("anomalyAlerts", platformReportService.anomalyAlerts());
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
