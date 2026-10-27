package com.csit314.platform.web;

import com.csit314.platform.service.CategoryManagementService;
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

    public PlatformManagerController(CategoryManagementService categoryManagementService,
                                     PlatformReportService platformReportService) {
        this.categoryManagementService = categoryManagementService;
        this.platformReportService = platformReportService;
    }

    // PM-02
    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categoryManagementService.listCategories());
        return "platform-manager-categories";
    }

    // PM-02
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

    // PM-03
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

    // PM-04
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

    // PM-05
    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reports", platformReportService.reports());
        return "platform-manager-reports";
    }
}
