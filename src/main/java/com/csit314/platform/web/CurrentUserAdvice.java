package com.csit314.platform.web;

import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// makes ${currentUser} available to every template (null when logged out)
@ControllerAdvice
public class CurrentUserAdvice {

    private final LoginSession loginSession;

    public CurrentUserAdvice(LoginSession loginSession) {
        this.loginSession = loginSession;
    }

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpServletRequest request) {
        return loginSession.current(request).orElse(null);
    }
}
