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
            UserAccount admin = authService.loginAdmin(email, password);
            loginSession.start(request, admin);
            return "redirect:/admin/users";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/login";
        }
    }

    // ADM-02
    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userManagementService.listAllUsers());
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
}
