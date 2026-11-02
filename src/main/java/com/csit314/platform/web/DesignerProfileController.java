package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
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

    private final InteriorDesignerRepository designerRepository;
    private final ProjectRepository projectRepository;
    private final RatingService ratingService;
    private final CustomerRepository customerRepository;
    private final LoginSession loginSession;

    public DesignerProfileController(InteriorDesignerRepository designerRepository,
                                     ProjectRepository projectRepository,
                                     RatingService ratingService,
                                     CustomerRepository customerRepository,
                                     LoginSession loginSession) {
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
        this.ratingService = ratingService;
        this.customerRepository = customerRepository;
        this.loginSession = loginSession;
    }

    @GetMapping("/designers/{id}")
    public String profile(@PathVariable Long id, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerRepository.findById(id).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", projectRepository.findByDesignerIdOrderByCreatedAtDesc(id));
        model.addAttribute("ratingSummary", ratingService.summaryFor(id));

        SessionUser current = loginSession.current(request).orElse(null);
        boolean canRate = false;
        if (current != null && current.hasRole(Role.CUSTOMER)) {
            Customer customer = customerRepository.findById(current.id()).orElse(null);
            canRate = customer != null && ratingService.canRate(customer, id);
        }
        model.addAttribute("canRate", canRate);
        return "designer-profile";
    }
}
