package com.csit314.platform.web;

import com.csit314.platform.model.UserAccount;
import com.csit314.platform.service.AuthService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.DesignerRegistrationForm;
import com.csit314.platform.web.session.LoginSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/designer")
public class DesignerAuthController {

    private final DesignerService designerService;
    private final AuthService authService;
    private final LoginSession loginSession;

    public DesignerAuthController(DesignerService designerService,
                                  AuthService authService,
                                  LoginSession loginSession) {
        this.designerService = designerService;
        this.authService = authService;
        this.loginSession = loginSession;
    }

    // IDR-01
    @PostMapping("/register")
    public String register(@ModelAttribute DesignerRegistrationForm form, RedirectAttributes redirect) {
        try {
            designerService.register(form);
            redirect.addFlashAttribute("successMessage",
                    "Thanks for registering! We'll review your company details and you can log in once you're approved.");
            return "redirect:/designer/login";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designer/register";
        }
    }

    // IDR-02
    @PostMapping("/login")
    public String login(@RequestParam(required = false) String email,
                        @RequestParam(required = false) String password,
                        HttpServletRequest request,
                        RedirectAttributes redirect) {
        try {
            UserAccount designer = authService.loginDesigner(email, password);
            loginSession.start(request, designer);
            return "redirect:/designer/dashboard";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designer/login";
        }
    }
}
