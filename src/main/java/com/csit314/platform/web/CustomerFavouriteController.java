package com.csit314.platform.web;

import com.csit314.platform.model.Customer;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.service.FavouriteService;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// IDR-10 - AuthInterceptor already makes sure a customer is logged in for all of these
@Controller
public class CustomerFavouriteController {

    private final FavouriteService favouriteService;
    private final CustomerRepository customerRepository;
    private final LoginSession loginSession;

    public CustomerFavouriteController(FavouriteService favouriteService,
                                       CustomerRepository customerRepository,
                                       LoginSession loginSession) {
        this.favouriteService = favouriteService;
        this.customerRepository = customerRepository;
        this.loginSession = loginSession;
    }

    @GetMapping("/favourites")
    public String favourites(HttpServletRequest request, Model model) {
        Customer customer = currentCustomer(request);
        model.addAttribute("favourites", favouriteService.listForCustomer(customer));
        return "favourites";
    }

    @PostMapping("/favourites/add")
    public String add(@RequestParam Long projectId, HttpServletRequest request, RedirectAttributes redirect) {
        favouriteService.add(currentCustomer(request), projectId);
        redirect.addFlashAttribute("successMessage", "Saved to your favourites.");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/favourites/remove")
    public String remove(@RequestParam Long projectId, HttpServletRequest request, RedirectAttributes redirect) {
        favouriteService.remove(currentCustomer(request), projectId);
        redirect.addFlashAttribute("successMessage", "Removed from your favourites.");
        return "redirect:/favourites";
    }

    private Customer currentCustomer(HttpServletRequest request) {
        SessionUser current = loginSession.current(request).orElseThrow();
        return customerRepository.findById(current.id()).orElseThrow();
    }
}
