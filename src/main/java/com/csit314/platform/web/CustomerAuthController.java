package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.service.AuthService;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.CustomerRegistrationForm;
import com.csit314.platform.web.session.LoginSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CustomerAuthController {

    private final CustomerService customerService;
    private final AuthService authService;
    private final LoginSession loginSession;

    public CustomerAuthController(CustomerService customerService,
                                  AuthService authService,
                                  LoginSession loginSession) {
        this.customerService = customerService;
        this.authService = authService;
        this.loginSession = loginSession;
    }

    // CUS-01: register and log straight in so they can start browsing
    @PostMapping("/register")
    public String register(@ModelAttribute CustomerRegistrationForm form,
                           HttpServletRequest request,
                           RedirectAttributes redirect) {
        try {
            Customer customer = customerService.register(form);
            loginSession.start(request, customer);
            redirect.addFlashAttribute("successMessage", "Welcome, " + customer.getFullName() + "! Your account is ready.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }

    // CUS-02: posted from the login modal in the navbar
    @PostMapping("/login")
    public String login(@RequestParam(required = false) String email,
                        @RequestParam(required = false) String password,
                        HttpServletRequest request,
                        RedirectAttributes redirect) {
        try {
            UserAccount customer = authService.loginCustomer(email, password);
            loginSession.start(request, customer);
            return "redirect:/favourites";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/search";
        }
    }

    // shared by every role
    @PostMapping("/logout")
    public String logout(HttpServletRequest request, RedirectAttributes redirect) {
        loginSession.end(request);
        redirect.addFlashAttribute("successMessage", "You have been logged out.");
        return "redirect:/search";
    }
}
