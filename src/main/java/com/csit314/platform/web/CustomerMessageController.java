package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.service.MessageService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.ContactDesignerForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// IDR-13 - standalone contact form, reachable directly by a designer's id rather than
// through /projects/{id} (still mock data, separate scope - see commit notes)
@Controller
@RequestMapping("/designers/{designerId}/contact")
public class CustomerMessageController {

    private final MessageService messageService;
    private final CustomerRepository customerRepository;
    private final InteriorDesignerRepository designerRepository;
    private final ProjectRepository projectRepository;
    private final LoginSession loginSession;

    public CustomerMessageController(MessageService messageService,
                                     CustomerRepository customerRepository,
                                     InteriorDesignerRepository designerRepository,
                                     ProjectRepository projectRepository,
                                     LoginSession loginSession) {
        this.messageService = messageService;
        this.customerRepository = customerRepository;
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String contactForm(@PathVariable Long designerId, Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerRepository.findById(designerId).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", projectRepository.findByDesignerIdOrderByCreatedAtDesc(designerId));
        return "contact-designer";
    }

    @PostMapping
    public String sendMessage(@PathVariable Long designerId,
                              @ModelAttribute ContactDesignerForm form,
                              HttpServletRequest request,
                              RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer sender = customerRepository.findById(current.id())
                    .orElseThrow(() -> new ServiceException("Customer account not found."));
            messageService.sendMessage(sender, designerId, form);
            redirect.addFlashAttribute("successMessage", "Your message has been sent.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designers/{designerId}/contact";
        }
    }
}
