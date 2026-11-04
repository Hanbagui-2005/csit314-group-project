package com.csit314.platform.web;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.service.CategorySuggestionService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.FavouriteService;
import com.csit314.platform.service.MessageService;
import com.csit314.platform.service.PortfolioAnalyticsService;
import com.csit314.platform.service.ProjectService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.service.WeeklySummaryService;
import com.csit314.platform.dto.CategorySuggestionResponse;
import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.dto.ProjectForm;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// designer pages, AuthInterceptor already makes sure they're logged in
@Controller
@RequestMapping("/designer")
public class DesignerController {

    private final ProjectService projectService;
    private final DesignerService designerService;
    private final CategorySuggestionService categorySuggestionService;
    private final PortfolioAnalyticsService portfolioAnalyticsService;
    private final MessageService messageService;
    private final FavouriteService favouriteService;
    private final WeeklySummaryService weeklySummaryService;
    private final LoginSession loginSession;

    public DesignerController(ProjectService projectService,
                              DesignerService designerService,
                              CategorySuggestionService categorySuggestionService,
                              PortfolioAnalyticsService portfolioAnalyticsService,
                              MessageService messageService,
                              FavouriteService favouriteService,
                              WeeklySummaryService weeklySummaryService,
                              LoginSession loginSession) {
        this.projectService = projectService;
        this.designerService = designerService;
        this.categorySuggestionService = categorySuggestionService;
        this.portfolioAnalyticsService = portfolioAnalyticsService;
        this.messageService = messageService;
        this.favouriteService = favouriteService;
        this.weeklySummaryService = weeklySummaryService;
        this.loginSession = loginSession;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        model.addAttribute("projects", projectService.listForDesigner(current.id()));
        InteriorDesigner designer = designerService.requireById(current.id());
        model.addAttribute("unreadMessageCount", messageService.unreadCount(designer));
        return "designer-dashboard";
    }

    // IDR-04
    @GetMapping("/projects/new")
    public String newProjectForm(Model model) {
        model.addAttribute("categories", projectService.listCategories());
        model.addAttribute("isEdit", false);
        return "project-form";
    }

    // IDR-07 - was a mock stub in PageController, now loads the real project
    @GetMapping("/projects/{id}/edit")
    public String editProjectForm(@PathVariable Long id, HttpServletRequest request, Model model,
                                  RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        InteriorDesigner designer = designerService.requireById(current.id());
        Project project = projectService.findEditable(designer, id).orElse(null);
        if (project == null) {
            redirect.addFlashAttribute("errorMessage", "That project couldn't be found.");
            return "redirect:/designer/dashboard";
        }
        model.addAttribute("categories", projectService.listCategoriesForEdit(project));
        model.addAttribute("isEdit", true);
        model.addAttribute("projectId", id);
        model.addAttribute("project", project);
        return "project-form";
    }

    // IDR-07
    @PostMapping("/projects/{id}")
    public String updateProject(@PathVariable Long id,
                                @ModelAttribute ProjectForm form,
                                @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
                                @RequestParam(value = "beforePhotos", required = false) List<MultipartFile> beforePhotos,
                                HttpServletRequest request,
                                RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            InteriorDesigner designer = designerService.requireById(current.id());
            Project project = projectService.updateProject(designer, id, form, photos, beforePhotos);
            redirect.addFlashAttribute("successMessage", "\"" + project.getTitle() + "\" has been updated.");
            return "redirect:/designer/dashboard";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designer/projects/{id}/edit";
        }
    }

    // IDR-09
    @PostMapping("/projects/{id}/milestone/advance")
    public String advanceMilestone(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            InteriorDesigner designer = designerService.requireById(current.id());
            Project project = projectService.advanceMilestone(designer, id);
            redirect.addFlashAttribute("successMessage",
                    "\"" + project.getTitle() + "\" moved to " + project.getMilestoneStage().getLabel() + ".");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/designer/dashboard";
    }

    // IDR-03 / IDR-11 / IDR-12
    @GetMapping("/analytics")
    public String analytics(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        InteriorDesigner designer = designerService.requireById(current.id());
        model.addAttribute("breakdown", portfolioAnalyticsService.categoryBreakdown(current.id()));
        model.addAttribute("shortlisters", favouriteService.shortlistersFor(designer));
        model.addAttribute("shortlistTrend", favouriteService.shortlistTrend(designer));
        return "designer-analytics";
    }

    // IDR-08
    @GetMapping("/weekly-summary")
    public String weeklySummary(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        InteriorDesigner designer = designerService.requireById(current.id());
        model.addAttribute("summary", weeklySummaryService.summarise(designer));
        return "designer-weekly-summary";
    }

    // IDR-06 - called from the new-project form while the designer is typing
    @GetMapping("/projects/suggest-category")
    @ResponseBody
    public CategorySuggestionResponse suggestCategory(@RequestParam(required = false) String title,
                                                       @RequestParam(required = false) String description) {
        Category suggestion = categorySuggestionService.suggest(title, description).orElse(null);
        if (suggestion == null) {
            return new CategorySuggestionResponse(null, null);
        }
        return new CategorySuggestionResponse(suggestion.getId(), suggestion.getName());
    }

    // company profile editing - was previously set once at registration and never editable again
         @GetMapping("/profile/edit")
    public String editCompanyProfileForm(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        InteriorDesigner designer = designerService.requireById(current.id());

        addCompanyProfileFields(
                model,
                designer.getCompany().getName(),
                designer.getCompany().getDescription(),
                designer.getCompany().getPhone(),
                designer.getCompany().getServiceArea(),
                designer.getCompany().getLicenseNumber());

        model.addAttribute(
                "licenseWarning",
                designerService.licenseReuseWarning(
                        current.id(),
                        designer.getCompany().getLicenseNumber()));

        return "designer-profile-edit";
    }

    @PostMapping("/profile")
    public String updateCompanyProfile(
            @ModelAttribute CompanyProfileForm form,
            HttpServletRequest request,
            RedirectAttributes redirect,
            Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();

        try {
            InteriorDesigner designer =
                    designerService.updateCompanyProfile(current.id(), form);

            loginSession.refresh(request, designer);
            redirect.addFlashAttribute(
                    "successMessage", "Your company profile has been updated.");

            return "redirect:/designer/profile/edit";
        } catch (ServiceException e) {
            model.addAttribute("errorMessage", e.getMessage());

            addCompanyProfileFields(
                    model,
                    form.companyName(),
                    form.companyDescription(),
                    form.companyPhone(),
                    form.serviceArea(),
                    form.licenseNumber());

            return "designer-profile-edit";
        }
    }

    private void addCompanyProfileFields(
            Model model,
            String name,
            String description,
            String phone,
            String serviceArea,
            String licenseNumber) {
        model.addAttribute("companyName", name);
        model.addAttribute("companyDescription", description);
        model.addAttribute("companyPhone", phone);
        model.addAttribute("companyServiceArea", serviceArea);
        model.addAttribute("licenseNumber", licenseNumber);
    }

    // IDR-04 / IDR-05 / IDR-07 / IDR-14
    @PostMapping("/projects")
    public String createProject(@ModelAttribute ProjectForm form,
                                @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
                                @RequestParam(value = "beforePhotos", required = false) List<MultipartFile> beforePhotos,
                                HttpServletRequest request,
                                RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            InteriorDesigner designer = designerService.requireById(current.id());
            Project project = projectService.createProject(designer, form, photos, beforePhotos);
            redirect.addFlashAttribute("successMessage",
                    "\"" + project.getTitle() + "\" has been added to your portfolio.");
            return "redirect:/designer/dashboard";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designer/projects/new";
        }
    }
}
