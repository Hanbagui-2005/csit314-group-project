package com.csit314.platform.web;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
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

    public DesignerProfileController(InteriorDesignerRepository designerRepository,
                                     ProjectRepository projectRepository) {
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/designers/{id}")
    public String profile(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerRepository.findById(id).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", projectRepository.findByDesignerIdOrderByCreatedAtDesc(id));
        return "designer-profile";
    }
}
