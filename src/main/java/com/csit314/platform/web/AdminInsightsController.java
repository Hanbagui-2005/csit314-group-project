package com.csit314.platform.web;

import com.csit314.platform.service.AdminInsightsService;
import com.csit314.platform.service.ComplaintService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.service.UserManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// ADM-10/11/13/14 - already behind AuthInterceptor's /admin/** protection
@Controller
@RequestMapping("/admin")
public class AdminInsightsController {

    private final ComplaintService complaintService;
    private final AdminInsightsService adminInsightsService;
    private final UserManagementService userManagementService;

    public AdminInsightsController(ComplaintService complaintService,
                                   AdminInsightsService adminInsightsService,
                                   UserManagementService userManagementService) {
        this.complaintService = complaintService;
        this.adminInsightsService = adminInsightsService;
        this.userManagementService = userManagementService;
    }

    // ADM-10 - designerId is optional; when given, narrows the list down to just that
    // designer's complaints instead of scanning every complaint in the system
    @GetMapping("/complaints")
    public String complaints(@RequestParam(required = false) Long designerId, Model model) {
        model.addAttribute("complaints",
                designerId == null ? complaintService.all() : complaintService.forDesigner(designerId));
        model.addAttribute("designerFilter", designerId);
        return "admin-complaints";
    }

    @PostMapping("/complaints/{id}/resolve")
    public String resolveComplaint(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            complaintService.resolve(id);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/complaints";
    }

    @PostMapping("/complaints/{id}/dismiss")
    public String dismissComplaint(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            complaintService.dismiss(id);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/complaints";
    }

    // ADM-11
    @GetMapping("/quality")
    public String quality(Model model) {
        model.addAttribute("summaries", adminInsightsService.designerQualitySummaries());
        return "admin-quality";
    }

    // ADM-13
    @GetMapping("/security")
    public String security(Model model) {
        model.addAttribute("attempts", adminInsightsService.recentFailedLoginAttempts());
        return "admin-security";
    }

    // ADM-14
    @GetMapping("/registration-alerts")
    public String registrationAlerts(Model model) {
        model.addAttribute("alerts", userManagementService.registrationAlerts());
        return "admin-registration-alerts";
    }
}
