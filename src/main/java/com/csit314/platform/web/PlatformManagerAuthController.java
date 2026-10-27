package com.csit314.platform.web;

import com.csit314.platform.model.UserAccount;
import com.csit314.platform.service.AuthService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.session.LoginSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/platform-manager")
public class PlatformManagerAuthController {

    private final AuthService authService;
    private final LoginSession loginSession;

    public PlatformManagerAuthController(AuthService authService, LoginSession loginSession) {
        this.authService = authService;
        this.loginSession = loginSession;
    }

    // PM-01
    @PostMapping("/login")
    public String login(@RequestParam(required = false) String email,
                        @RequestParam(required = false) String password,
                        HttpServletRequest request,
                        RedirectAttributes redirect) {
        try {
            UserAccount manager = authService.loginPlatformManager(email, password, request.getRemoteAddr());
            loginSession.start(request, manager);
            return "redirect:/platform-manager/categories";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/platform-manager/login";
        }
    }
}
