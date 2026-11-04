package com.csit314.platform.web;

import com.csit314.platform.model.Role;
import com.csit314.platform.service.UserManagementService;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// makes ${currentUser} available to every template (null when logged out)
@ControllerAdvice
public class CurrentUserAdvice {

    private final LoginSession loginSession;
    private final UserManagementService userManagementService;

    public CurrentUserAdvice(LoginSession loginSession, UserManagementService userManagementService) {
        this.loginSession = loginSession;
        this.userManagementService = userManagementService;
    }

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpServletRequest request) {
        return loginSession.current(request).orElse(null);
    }

    // ADM-12 - a count shown only on /admin/users was easy to never see if you logged in
    // and went straight to e.g. Complaints; surfacing it on every admin page via the navbar
    // (see fragments/layout.html's Admin dropdown) means a waiting registration actually
    // gets noticed instead of sitting there until someone happens to visit Users
    @ModelAttribute("pendingDesignerNotificationCount")
    public Long pendingDesignerNotificationCount(HttpServletRequest request) {
        SessionUser current = loginSession.current(request).orElse(null);
        if (current == null || !current.hasRole(Role.ADMIN)) {
            return null;
        }
        return userManagementService.pendingDesignerCount();
    }
}
