package com.csit314.platform.web;

import com.csit314.platform.service.CategoryManagementService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.PlatformReportService;
import com.csit314.platform.service.ServiceException;
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

    public PlatformManagerController(CategoryManagementService categoryManagementService,
                                     PlatformReportService platformReportService,
                                     DesignerService designerService) {
        this.categoryManagementService = categoryManagementService;
        this.platformReportService = platformReportService;
        this.designerService = designerService;
    }

    // PM-01
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categoryManagementService.listCategories());
        return "platform-manager-categories";
    }

    // PM-01
    @PostMapping("/categories")
    public String addCategory(@RequestParam(required = false) String name, RedirectAttributes redirect) {
        try {
            categoryManagementService.addCategory(name);
            redirect.addFlashAttribute("successMessage", "Category added.");
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

    // category management - supports PM-01; a hard delete-with-guard, not PM-13's
    // soft-delete (not yet implemented)
    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            categoryManagementService.deleteCategory(id);
            redirect.addFlashAttribute("successMessage", "Category deleted.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/platform-manager/categories";
    }

    // PM-06 (daily figures) / PM-08 (monthly summary) combined into one report with a
    // bonus weekly window; PM-09 (PDF/CSV export) and PM-12 (scheduled generation) aren't implemented
    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reports", platformReportService.reports());
        model.addAttribute("categoryCoverage", platformReportService.categoryDesignerCoverage());
        // PM-04
        model.addAttribute("categoryViewRanking", platformReportService.categoryViewRanking());
        return "platform-manager-reports";
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
