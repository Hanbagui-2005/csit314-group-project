package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.service.ComplaintService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.ComplaintForm;
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

// ADM-10 - same shape as CustomerMessageController, a standalone form by designer id
@Controller
@RequestMapping("/designers/{designerId}/complaint")
public class CustomerComplaintController {

    private final ComplaintService complaintService;
    private final CustomerRepository customerRepository;
    private final InteriorDesignerRepository designerRepository;
    private final LoginSession loginSession;

    public CustomerComplaintController(ComplaintService complaintService,
                                       CustomerRepository customerRepository,
                                       InteriorDesignerRepository designerRepository,
                                       LoginSession loginSession) {
        this.complaintService = complaintService;
        this.customerRepository = customerRepository;
        this.designerRepository = designerRepository;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String complaintForm(@PathVariable Long designerId, Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerRepository.findById(designerId).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        return "file-complaint";
    }

    @PostMapping
    public String submit(@PathVariable Long designerId,
                         @ModelAttribute ComplaintForm form,
                         HttpServletRequest request,
                         RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer customer = customerRepository.findById(current.id())
                    .orElseThrow(() -> new ServiceException("Customer account not found."));
            complaintService.submit(customer, designerId, form);
            redirect.addFlashAttribute("successMessage", "Your complaint has been submitted to our team.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designers/{designerId}/complaint";
        }
    }
}
