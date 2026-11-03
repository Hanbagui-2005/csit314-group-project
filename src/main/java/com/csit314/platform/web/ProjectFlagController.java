package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.Project;
import com.csit314.platform.service.BrowseService;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.ProjectFlagService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// PM-11 - a customer reporting a project they think is inappropriate
@Controller
@RequestMapping("/projects/{projectId}/report")
public class ProjectFlagController {

    private final ProjectFlagService projectFlagService;
    private final BrowseService browseService;
    private final CustomerService customerService;
    private final LoginSession loginSession;

    public ProjectFlagController(ProjectFlagService projectFlagService, BrowseService browseService,
                                 CustomerService customerService, LoginSession loginSession) {
        this.projectFlagService = projectFlagService;
        this.browseService = browseService;
        this.customerService = customerService;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String reportForm(@PathVariable Long projectId, HttpServletRequest request, Model model,
                             RedirectAttributes redirect) {
        Project project;
        try {
            // countThisView=false, viewerDesignerId=null - opening the report form
            // shouldn't itself count as a view
            project = browseService.viewProject(projectId, null, false);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/search";
        }
        model.addAttribute("project", project);
        return "report-project";
    }

    @PostMapping
    public String submit(@PathVariable Long projectId, @RequestParam(required = false) String reason,
                         HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer customer = customerService.requireById(current.id());
            projectFlagService.report(customer, projectId, reason);
            redirect.addFlashAttribute("successMessage", "Thanks - we'll take a look.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/projects/{projectId}/report";
        }
        return "redirect:/projects/{projectId}";
    }
}
