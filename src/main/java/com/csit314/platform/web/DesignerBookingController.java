package com.csit314.platform.web;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.service.BookingService;
import com.csit314.platform.service.DesignerService;
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

import java.util.function.BiConsumer;

// CUS-11 - designer's side of the booking inbox
@Controller
@RequestMapping("/designer/bookings")
public class DesignerBookingController {

    private final BookingService bookingService;
    private final DesignerService designerService;
    private final LoginSession loginSession;

    public DesignerBookingController(BookingService bookingService,
                                     DesignerService designerService,
                                     LoginSession loginSession) {
        this.bookingService = bookingService;
        this.designerService = designerService;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String bookings(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        var designer = designerService.requireById(current.id());
        model.addAttribute("bookings", bookingService.bookingsFor(designer));
        return "designer-bookings";
    }

    @PostMapping("/{id}/accept")
    public String accept(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        return respond(id, request, redirect, bookingService::accept);
    }

    @PostMapping("/{id}/decline")
    public String decline(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        return respond(id, request, redirect, bookingService::decline);
    }

    private String respond(Long id, HttpServletRequest request, RedirectAttributes redirect,
                           BiConsumer<InteriorDesigner, Long> action) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            var designer = designerService.requireById(current.id());
            action.accept(designer, id);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/designer/bookings";
    }
}
