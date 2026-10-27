package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.service.BrowseService;
import com.csit314.platform.service.FavouriteService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// serves the main public pages
@Controller
public class PageController {

    private final BrowseService browseService;
    private final FavouriteService favouriteService;
    private final CategoryRepository categoryRepository;
    private final CustomerRepository customerRepository;
    private final LoginSession loginSession;

    public PageController(BrowseService browseService,
                          FavouriteService favouriteService,
                          CategoryRepository categoryRepository,
                          CustomerRepository customerRepository,
                          LoginSession loginSession) {
        this.browseService = browseService;
        this.favouriteService = favouriteService;
        this.categoryRepository = categoryRepository;
        this.customerRepository = customerRepository;
        this.loginSession = loginSession;
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    // IDR-10/CUS-04/CUS-09/CUS-12 - was hardcoded sample data, now real projects with real filters
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) Integer maxBudget,
                          @RequestParam(required = false) Integer minRoomSize,
                          @RequestParam(required = false, defaultValue = "false") boolean verifiedOnly,
                          Model model) {
        model.addAttribute("query", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("maxBudget", maxBudget);
        model.addAttribute("minRoomSize", minRoomSize);
        model.addAttribute("verifiedOnly", verifiedOnly);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("projects", browseService.search(q, category, maxBudget, minRoomSize, verifiedOnly));
        model.addAttribute("budgetEstimate", browseService.estimateBudget(category).orElse(null));
        return "search";
    }

    // IDR-10 - counts as a real view now, and shows whether the current customer already saved it
    @GetMapping("/projects/{id}")
    public String projectDetail(@PathVariable Long id, HttpServletRequest request, Model model,
                                RedirectAttributes redirect) {
        Project project;
        try {
            project = browseService.viewProject(id);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/search";
        }
        model.addAttribute("project", project);

        SessionUser current = loginSession.current(request).orElse(null);
        boolean isShortlisted = false;
        if (current != null && current.hasRole(Role.CUSTOMER)) {
            Customer customer = customerRepository.findById(current.id()).orElse(null);
            isShortlisted = customer != null && favouriteService.isShortlisted(customer, id);
        }
        model.addAttribute("isShortlisted", isShortlisted);
        return "project-detail";
    }

    // IDR-04/05/07 - real dashboard, new-project form and edit form now live in DesignerController

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @GetMapping("/designer/register")
    public String designerRegisterForm() {
        return "designer-register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @GetMapping("/designer/login")
    public String designerLoginForm() {
        return "designer-login";
    }

    @GetMapping("/admin/login")
    public String adminLoginForm() {
        return "admin-login";
    }

    @GetMapping("/platform-manager/login")
    public String platformManagerLoginForm() {
        return "platform-manager-login";
    }

}
