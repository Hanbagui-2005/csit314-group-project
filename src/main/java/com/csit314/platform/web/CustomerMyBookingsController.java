package com.csit314.platform.web;

import com.csit314.platform.service.BookingService;
import com.csit314.platform.service.CustomerService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// ADM-11 - customer's side of their own booking list, with a cancel action so there's real
// data behind the admin cancellation-rate report
@Controller
@RequestMapping("/bookings")
public class CustomerMyBookingsController {

    private final BookingService bookingService;
    private final CustomerService customerService;
    private final LoginSession loginSession;

    public CustomerMyBookingsController(BookingService bookingService,
                                        CustomerService customerService,
                                        LoginSession loginSession) {
        this.bookingService = bookingService;
        this.customerService = customerService;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String myBookings(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        var customer = customerService.requireById(current.id());
        model.addAttribute("bookings", bookingService.bookingsFor(customer));
        return "my-bookings";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            var customer = customerService.requireById(current.id());
            bookingService.cancel(customer, id);
            redirect.addFlashAttribute("successMessage", "Booking cancelled.");
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bookings";
    }
}
