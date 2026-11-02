package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.service.BrowseService;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.MessageService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.dto.ContactDesignerForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// IDR-13 - standalone contact form, reachable directly by a designer's id rather than
// through /projects/{id} (still mock data, separate scope - see commit notes)
@Controller
@RequestMapping("/designers/{designerId}/contact")
public class CustomerMessageController {

    private final MessageService messageService;
    private final CustomerService customerService;
    private final DesignerService designerService;
    private final BrowseService browseService;
    private final LoginSession loginSession;

    public CustomerMessageController(MessageService messageService,
                                     CustomerService customerService,
                                     DesignerService designerService,
                                     BrowseService browseService,
                                     LoginSession loginSession) {
        this.messageService = messageService;
        this.customerService = customerService;
        this.designerService = designerService;
        this.browseService = browseService;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String contactForm(@PathVariable Long designerId,
                              @RequestParam(required = false) Long projectId,
                              Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerService.findById(designerId).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", browseService.listForDesigner(designerId));
        model.addAttribute("selectedProjectId", projectId);
        return "contact-designer";
    }

    @PostMapping
    public String sendMessage(@PathVariable Long designerId,
                              @ModelAttribute ContactDesignerForm form,
                              HttpServletRequest request,
                              RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer sender = customerService.requireById(current.id());
            messageService.sendMessage(sender, designerId, form);
            redirect.addFlashAttribute("successMessage", "Your message has been sent.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designers/{designerId}/contact";
        }
    }
}
