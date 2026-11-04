package com.csit314.platform.web;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.service.AuthService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.LicenseVerificationService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.service.UserManagementService;
import com.csit314.platform.web.session.LoginSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AuthService authService;
    private final UserManagementService userManagementService;
    private final LoginSession loginSession;
    private final DesignerService designerService;
    private final LicenseVerificationService licenseVerificationService;

    public AdminController(
            AuthService authService,
            UserManagementService userManagementService,
            LoginSession loginSession,
            DesignerService designerService,
            LicenseVerificationService licenseVerificationService) {
        this.authService = authService;
        this.userManagementService = userManagementService;
        this.loginSession = loginSession;
        this.designerService = designerService;
        this.licenseVerificationService = licenseVerificationService;
    }

    @PostMapping("/login")
    public String login(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        try {
            UserAccount admin = authService.loginAdmin(
                    email, password, request.getRemoteAddr());
            loginSession.start(request, admin);
            return "redirect:/admin/users";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/login";
        }
    }

    // ADM-08 - status/role are optional filters, so the bulk-approve/suspend buttons
    // can act on "accounts that match a filter" instead of only whatever's manually ticked
    @GetMapping("/users")
    public String users(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            Model model) {
        model.addAttribute("users", userManagementService.listAllUsers(status, role));
        model.addAttribute("statusFilter", status);
        model.addAttribute("roleFilter", role);
        model.addAttribute(
                "pendingCount", userManagementService.pendingDesignerCount());
        return "admin-users";
    }

    @PostMapping("/users/{id}/approve")
    public String approve(
            @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            InteriorDesigner designer =
                    userManagementService.approveDesigner(id);
            redirect.addFlashAttribute(
                    "successMessage",
                    designer.getCompany().getName() + " has been approved.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reject")
    public String reject(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        try {
            InteriorDesigner designer =
                    userManagementService.rejectDesigner(id, reason);
            redirect.addFlashAttribute(
                    "successMessage",
                    designer.getCompany().getName()
                            + "'s registration has been rejected.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/suspend")
    public String suspend(
            @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            userManagementService.suspend(id);
            redirect.addFlashAttribute(
                    "successMessage", "Account suspended.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reactivate")
    public String reactivate(
            @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            userManagementService.reactivate(id);
            redirect.addFlashAttribute(
                    "successMessage", "Account reactivated.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/unlock")
    public String unlock(
            @PathVariable Long id,
            RedirectAttributes redirect) {
        try {
            userManagementService.unlock(id);
            redirect.addFlashAttribute(
                    "successMessage", "Account unlocked.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

      @GetMapping("/users/{id}/license")
    public String license(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirect) {
        try {
            InteriorDesigner designer = designerService.requireById(id);

            model.addAttribute("designer", designer);
            model.addAttribute(
                    "history", licenseVerificationService.historyFor(id));
            model.addAttribute(
                    "licenseWarning",
                    designerService.licenseReuseWarning(
                            id, designer.getCompany().getLicenseNumber()));

            return "admin-license-verification";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/users/{id}/license/verify")
    public String verifyLicense(
            @PathVariable Long id,
            @RequestParam(required = false) String licenseNumber,
            @RequestParam(required = false) String note,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        Long adminId = loginSession.current(request).orElseThrow().id();

        try {
            licenseVerificationService.verify(
                    id, adminId, licenseNumber, note);
            redirect.addFlashAttribute(
                    "successMessage", "License verification recorded.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/users/" + id + "/license";
    }

    @PostMapping("/users/{id}/license/revoke")
    public String revokeLicense(
            @PathVariable Long id,
            @RequestParam(required = false) String licenseNumber,
            @RequestParam(required = false) String note,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        Long adminId = loginSession.current(request).orElseThrow().id();

        try {
            licenseVerificationService.revoke(
                    id, adminId, licenseNumber, note);
            redirect.addFlashAttribute(
                    "successMessage", "License verification revoked.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/users/" + id + "/license";
    }

    @PostMapping("/users/bulk-approve")
    public String bulkApprove(
            @RequestParam(required = false) List<Long> ids,
            RedirectAttributes redirect) {
        int count = userManagementService.bulkApproveDesigners(
                ids == null ? List.of() : ids);
        redirect.addFlashAttribute(
                "successMessage", count + " registration(s) approved.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/bulk-suspend")
    public String bulkSuspend(
            @RequestParam(required = false) List<Long> ids,
            RedirectAttributes redirect) {
        int count = userManagementService.bulkSuspend(
                ids == null ? List.of() : ids);
        redirect.addFlashAttribute(
                "successMessage", count + " account(s) suspended.");
        return "redirect:/admin/users";
    }
}
