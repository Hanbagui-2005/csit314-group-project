package com.csit314.platform.web.session;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Optional;

// sends visitors to the right login page if they open a page their role can't see
public class AuthInterceptor implements HandlerInterceptor {

    private final LoginSession loginSession;
    private final Role requiredRole;
    private final String loginPath;
    private final UserAccountRepository userAccountRepository;

    public AuthInterceptor(LoginSession loginSession, Role requiredRole, String loginPath,
                           UserAccountRepository userAccountRepository) {
        this.loginSession = loginSession;
        this.requiredRole = requiredRole;
        this.loginPath = loginPath;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Optional<SessionUser> user = loginSession.current(request);
        if (user.isPresent() && user.get().hasRole(requiredRole)) {
            if (stillActive(user.get())) {
                return true;
            }
            // ADM-06/08 - re-checked against the database on every request, so a suspension
            // takes effect immediately instead of waiting for the session to expire on its
            // own. Only reached once the role already matched - a user with the wrong role
            // for this route (e.g. a designer clicking a customer-only link) is still just
            // redirected, not logged out everywhere
            loginSession.end(request);
        }

        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("errorMessage", "Please log in as " + article(requiredRole) + " to continue.");
        RequestContextUtils.saveOutputFlashMap(loginPath, request, response);
        response.sendRedirect(request.getContextPath() + loginPath);
        return false;
    }

    private boolean stillActive(SessionUser user) {
        return userAccountRepository.existsByIdAndStatus(user.id(), AccountStatus.ACTIVE);
    }

    private static String article(Role role) {
        return role == Role.ADMIN ? "an admin" : "a " + role.getLabel().toLowerCase();
    }
}
