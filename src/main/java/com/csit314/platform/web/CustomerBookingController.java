package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.service.BookingService;
import com.csit314.platform.service.ServiceException;
import com.csit314.platform.web.dto.BookingRequestForm;
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

// CUS-11 - same shape as CustomerMessageController, a standalone form by designer id
@Controller
@RequestMapping("/designers/{designerId}/book")
public class CustomerBookingController {

    private final BookingService bookingService;
    private final CustomerRepository customerRepository;
    private final InteriorDesignerRepository designerRepository;
    private final ProjectRepository projectRepository;
    private final LoginSession loginSession;

    public CustomerBookingController(BookingService bookingService,
                                     CustomerRepository customerRepository,
                                     InteriorDesignerRepository designerRepository,
                                     ProjectRepository projectRepository,
                                     LoginSession loginSession) {
        this.bookingService = bookingService;
        this.customerRepository = customerRepository;
        this.designerRepository = designerRepository;
        this.projectRepository = projectRepository;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String bookingForm(@PathVariable Long designerId,
                              @RequestParam(required = false) Long projectId,
                              Model model, RedirectAttributes redirect) {
        InteriorDesigner designer = designerRepository.findById(designerId).orElse(null);
        if (designer == null) {
            redirect.addFlashAttribute("errorMessage", "That designer couldn't be found.");
            return "redirect:/search";
        }
        model.addAttribute("designer", designer);
        model.addAttribute("projects", projectRepository.findByDesignerIdOrderByCreatedAtDesc(designerId));
        model.addAttribute("selectedProjectId", projectId);
        return "book-consultation";
    }

    @PostMapping
    public String requestBooking(@PathVariable Long designerId,
                                 @ModelAttribute BookingRequestForm form,
                                 HttpServletRequest request,
                                 RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            Customer customer = customerRepository.findById(current.id())
                    .orElseThrow(() -> new ServiceException("Customer account not found."));
            bookingService.requestBooking(customer, designerId, form);
            redirect.addFlashAttribute("successMessage", "Your booking request has been sent.");
            return "redirect:/search";
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/designers/{designerId}/book";
        }
    }
}
