package com.csit314.platform.web;

import com.csit314.platform.web.dto.DesignerProjectStat;
import com.csit314.platform.web.dto.FavouriteItem;
import com.csit314.platform.web.dto.ProjectCard;
import com.csit314.platform.web.dto.ProjectDetail;
import com.csit314.platform.web.dto.UserSummary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// serves the main pages with hardcoded data until the backend/DB is ready
@Controller
public class PageController {

    private static final List<String> CATEGORIES = List.of("Kitchen", "Living Room", "Bedroom", "Minimal", "Budget");

    private static final List<ProjectCard> SAMPLE_PROJECTS = List.of(
            new ProjectCard(1L, "Modern minimalist kitchen", "Studio Lume", "/images/placeholder.svg", "Kitchen"),
            new ProjectCard(2L, "Cozy reading nook", "Studio Lume", "/images/placeholder.svg", "Living Room"),
            new ProjectCard(3L, "Budget-friendly bedroom refresh", "North & Pine", "/images/placeholder.svg", "Bedroom"),
            new ProjectCard(4L, "Open-plan living and dining", "North & Pine", "/images/placeholder.svg", "Living Room")
    );

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String category,
                          Model model) {
        model.addAttribute("query", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("projects", SAMPLE_PROJECTS);
        return "search";
    }

    @GetMapping("/projects/{id}")
    public String projectDetail(@PathVariable Long id, Model model) {
        ProjectDetail detail = new ProjectDetail(
                id,
                "Modern minimalist kitchen",
                "A full kitchen renovation focused on clean lines, light oak cabinetry and a neutral "
                        + "palette. Includes new island seating and integrated appliances.",
                "/images/placeholder.svg",
                "Studio Lume",
                101L,
                "Kitchen"
        );
        model.addAttribute("project", detail);
        return "project-detail";
    }

    @GetMapping("/favourites")
    public String favourites(Model model) {
        List<FavouriteItem> favourites = List.of(
                new FavouriteItem(1L, "Modern minimalist kitchen", "Studio Lume", "/images/placeholder.svg"),
                new FavouriteItem(3L, "Budget-friendly bedroom refresh", "North & Pine", "/images/placeholder.svg")
        );
        model.addAttribute("favourites", favourites);
        return "favourites";
    }

    @GetMapping("/designer/dashboard")
    public String designerDashboard(Model model) {
        List<DesignerProjectStat> myProjects = List.of(
                new DesignerProjectStat(1L, "Modern minimalist kitchen", "/images/placeholder.svg", 42, 8),
                new DesignerProjectStat(2L, "Cozy reading nook", "/images/placeholder.svg", 19, 3)
        );
        model.addAttribute("projects", myProjects);
        return "designer-dashboard";
    }

    @GetMapping("/designer/projects/new")
    public String newProjectForm(Model model) {
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("isEdit", false);
        return "project-form";
    }

    @GetMapping("/designer/projects/{id}/edit")
    public String editProjectForm(@PathVariable Long id, Model model) {
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("isEdit", true);
        model.addAttribute("projectId", id);
        return "project-form";
    }

    @GetMapping("/admin/users")
    public String adminUsers(Model model) {
        List<UserSummary> users = List.of(
                new UserSummary(1L, "Alex Tan", "alex.tan@example.com", "Customer", "Active"),
                new UserSummary(2L, "Studio Lume", "contact@studiolume.com", "Interior Designer", "Active"),
                new UserSummary(3L, "North & Pine", "hello@northandpine.com", "Interior Designer", "Pending"),
                new UserSummary(4L, "Priya Shah", "priya.shah@example.com", "Customer", "Active"),
                new UserSummary(5L, "Dev House Studio", "team@devhousestudio.com", "Interior Designer", "Suspended")
        );
        model.addAttribute("users", users);
        return "admin-users";
    }

}
