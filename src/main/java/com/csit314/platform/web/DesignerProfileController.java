package com.csit314.platform.web;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Role;
import com.csit314.platform.service.BrowseService;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.RatingService;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// CUS-10 - public designer profile, reached from a project listing
@Controller
public class DesignerProfileController {

    private final DesignerService designerService;
    private final BrowseService browseService;
    private final RatingService ratingService;
    private final CustomerService customerService;
    private final LoginSession loginSession;

    public DesignerProfileController(DesignerService designerService,
                                     BrowseService browseService,
                                     RatingService ratingService,
                                     CustomerService customerService,
                                     LoginSession loginSession) {
        this.designerService = designerService;
        this.browseService = browseService;
        this.ratingService = ratingService;
        this.customerService = customerService;
        this.loginSession = loginSession;
    }

    @GetMapping("/designers/{id}")
    public String profile(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerService.findById(id).orElse(null);
        // CUS-10 - a pending, rejected or suspended designer shouldn't have a public profile
        if (designer == null || designer.getStatus() != AccountStatus.ACTIVE) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", browseService.listForDesigner(id));
        model.addAttribute("ratingSummary", ratingService.summaryFor(id));

        SessionUser current = loginSession.current(request).orElse(null);
        boolean canRate = false;
        if (current != null && current.hasRole(Role.CUSTOMER)) {
            Customer customer = customerService.findById(current.id()).orElse(null);
            canRate = customer != null && ratingService.canRate(customer, id);
        }
        model.addAttribute("canRate", canRate);
        return "designer-profile";
    }
}
