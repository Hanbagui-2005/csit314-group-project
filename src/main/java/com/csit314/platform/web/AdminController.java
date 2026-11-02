package com.csit314.platform.web;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.service.AuthService;
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

    public AdminController(AuthService authService,
                           UserManagementService userManagementService,
                           LoginSession loginSession) {
        this.authService = authService;
        this.userManagementService = userManagementService;
        this.loginSession = loginSession;
    }

    // ADM-01
    @PostMapping("/login")
    public String login(@RequestParam(required = false) String email,
                        @RequestParam(required = false) String password,
                        HttpServletRequest request,
                        RedirectAttributes redirect) {
        try {
            UserAccount admin = authService.loginAdmin(email, password, request.getRemoteAddr());
            loginSession.start(request, admin);
            return "redirect:/admin/users";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/login";
        }
    }

    // ADM-02 / ADM-12
    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userManagementService.listAllUsers());
        model.addAttribute("pendingCount", userManagementService.pendingDesignerCount());
        return "admin-users";
    }

    // ADM-03
    @PostMapping("/users/{id}/approve")
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            InteriorDesigner designer = userManagementService.approveDesigner(id);
            redirect.addFlashAttribute("successMessage",
                    designer.getCompany().getName() + " has been approved.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-04
    @PostMapping("/users/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes redirect) {
        try {
            InteriorDesigner designer = userManagementService.rejectDesigner(id, reason);
            redirect.addFlashAttribute("successMessage",
                    designer.getCompany().getName() + "'s registration has been rejected.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-06
    @PostMapping("/users/{id}/suspend")
    public String suspend(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            userManagementService.suspend(id);
            redirect.addFlashAttribute("successMessage", "Account suspended.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-06
    @PostMapping("/users/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            userManagementService.reactivate(id);
            redirect.addFlashAttribute("successMessage", "Account reactivated.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-09
    @PostMapping("/users/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            userManagementService.unlock(id);
            redirect.addFlashAttribute("successMessage", "Account unlocked.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-05
    @PostMapping("/users/{id}/toggle-verified")
    public String toggleVerified(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            InteriorDesigner designer = userManagementService.toggleVerified(id);
            redirect.addFlashAttribute("successMessage",
                    designer.getCompany().getName() + " is now " + (designer.isVerified() ? "verified." : "unverified."));
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ADM-08
    @PostMapping("/users/bulk-approve")
    public String bulkApprove(@RequestParam(required = false) List<Long> ids, RedirectAttributes redirect) {
        int count = userManagementService.bulkApproveDesigners(ids == null ? List.of() : ids);
        redirect.addFlashAttribute("successMessage", count + " registration(s) approved.");
        return "redirect:/admin/users";
    }

    // ADM-08
    @PostMapping("/users/bulk-suspend")
    public String bulkSuspend(@RequestParam(required = false) List<Long> ids, RedirectAttributes redirect) {
        int count = userManagementService.bulkSuspend(ids == null ? List.of() : ids);
        redirect.addFlashAttribute("successMessage", count + " account(s) suspended.");
        return "redirect:/admin/users";
    }
}
