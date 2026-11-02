package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.RatingService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.dto.RatingForm;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// CUS-13 - no standalone form page, submits straight from the designer profile
@Controller
@RequestMapping("/designers/{designerId}/rate")
public class CustomerRatingController {

    private final RatingService ratingService;
    private final CustomerService customerService;
    private final LoginSession loginSession;

    public CustomerRatingController(RatingService ratingService,
                                    CustomerService customerService,
                                    LoginSession loginSession) {
        this.ratingService = ratingService;
        this.customerService = customerService;
        this.loginSession = loginSession;
    }

    @PostMapping
    public String rate(@PathVariable Long designerId, @ModelAttribute RatingForm form,
                       HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer customer = customerService.requireById(current.id());
            ratingService.rate(customer, designerId, form);
            redirect.addFlashAttribute("successMessage", "Thanks for the rating!");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/designers/" + designerId;
    }
}
