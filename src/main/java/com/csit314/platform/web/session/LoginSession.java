package com.csit314.platform.web.session;

import com.csit314.platform.model.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

// starts, reads and ends the server-side login session (the browser only holds the JSESSIONID cookie)
@Component
public class LoginSession {

    public static final String CURRENT_USER = "currentUser";

    public SessionUser start(HttpServletRequest request, UserAccount user) {
        request.getSession(true);
        // new session id on login so an id set before login can't be reused (session fixation)
        request.changeSessionId();
        SessionUser sessionUser = SessionUser.from(user);
        request.getSession().setAttribute(CURRENT_USER, sessionUser);
        return sessionUser;
    }

    // Anthony's review (PR #25) - SessionUser is a snapshot taken at login; nothing
    // updated it after a profile edit changed the designer's display name, so the
    // navbar kept showing the old company name until the next log in. Call this after
    // any change to the logged-in user's own name/email/role so the session catches up
    public void refresh(HttpServletRequest request, UserAccount user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(CURRENT_USER, SessionUser.from(user));
        }
    }

    public Optional<SessionUser> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((SessionUser) session.getAttribute(CURRENT_USER));
    }

    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
