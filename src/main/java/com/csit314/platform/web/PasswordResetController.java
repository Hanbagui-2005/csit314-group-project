package com.csit314.platform.web;

import com.csit314.platform.service.PasswordResetService;
import com.csit314.platform.service.ServiceException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// CUS-03
@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam(required = false) String email, RedirectAttributes redirect) {
        passwordResetService.requestReset(email);
        // same reply whether or not the email exists, so this can't be used to find accounts
        redirect.addFlashAttribute("successMessage",
                "If an account exists for that email, we've sent a link to reset your password.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetForm(@RequestParam(required = false) String token,
                            Model model,
                            RedirectAttributes redirect) {
        if (!passwordResetService.isTokenValid(token)) {
            redirect.addFlashAttribute("errorMessage", "This reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                RedirectAttributes redirect) {
        try {
            passwordResetService.resetPassword(token, password, confirmPassword);
            redirect.addFlashAttribute("successMessage", "Your password has been reset. You can now log in.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            redirect.addAttribute("token", token);
            return "redirect:/reset-password";
        }
    }
}
