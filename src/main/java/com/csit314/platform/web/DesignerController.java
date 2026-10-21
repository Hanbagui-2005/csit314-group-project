package com.csit314.platform.web;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.service.CategorySuggestionService;
import com.csit314.platform.service.ProjectService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.CategorySuggestionResponse;
import com.csit314.platform.web.dto.ProjectForm;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    private final ProjectRepository projectRepository;
    private final CategoryRepository categoryRepository;
    private final InteriorDesignerRepository designerRepository;
    private final ProjectService projectService;
    private final CategorySuggestionService categorySuggestionService;
    private final LoginSession loginSession;

    public DesignerController(ProjectRepository projectRepository,
                              CategoryRepository categoryRepository,
                              InteriorDesignerRepository designerRepository,
                              ProjectService projectService,
                              CategorySuggestionService categorySuggestionService,
                              LoginSession loginSession) {
        this.projectRepository = projectRepository;
        this.categoryRepository = categoryRepository;
        this.designerRepository = designerRepository;
        this.projectService = projectService;
        this.categorySuggestionService = categorySuggestionService;
        this.loginSession = loginSession;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        model.addAttribute("projects", projectRepository.findByDesignerIdOrderByCreatedAtDesc(current.id()));
        return "designer-dashboard";
    }

    // IDR-04
    @GetMapping("/projects/new")
    public String newProjectForm(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("isEdit", false);
        return "project-form";
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

    // IDR-04 / IDR-05
    @PostMapping("/projects")
    public String createProject(@ModelAttribute ProjectForm form,
                                @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
                                HttpServletRequest request,
                                RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            InteriorDesigner designer = designerRepository.findById(current.id())
                    .orElseThrow(() -> new ServiceException("Designer account not found."));
            Project project = projectService.createProject(designer, form, photos);
            redirect.addFlashAttribute("successMessage",
                    "\"" + project.getTitle() + "\" has been added to your portfolio.");
            return "redirect:/designer/dashboard";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designer/projects/new";
        }
    }
}
