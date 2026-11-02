package com.csit314.platform.web;

import com.csit314.platform.service.DesignerService;
import com.csit314.platform.service.MessageService;
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

// IDR-13 - designer's inbox; AuthInterceptor already makes sure they're logged in
@Controller
@RequestMapping("/designer/messages")
public class DesignerMessageController {

    private final MessageService messageService;
    private final DesignerService designerService;
    private final LoginSession loginSession;

    public DesignerMessageController(MessageService messageService,
                                     DesignerService designerService,
                                     LoginSession loginSession) {
        this.messageService = messageService;
        this.designerService = designerService;
        this.loginSession = loginSession;
    }

    @GetMapping
    public String inbox(HttpServletRequest request, Model model) {
        SessionUser current = loginSession.current(request).orElseThrow();
        var designer = designerService.requireById(current.id());
        model.addAttribute("messages", messageService.inbox(designer));
        return "designer-messages";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser current = loginSession.current(request).orElseThrow();
        try {
            var designer = designerService.requireById(current.id());
            messageService.markRead(designer, id);
        } catch (ServiceException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/designer/messages";
    }
}
