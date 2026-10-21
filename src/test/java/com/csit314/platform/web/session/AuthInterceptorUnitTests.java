package com.csit314.platform.web.session;

import com.csit314.platform.model.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import static org.assertj.core.api.Assertions.*;

/** HTTP role enforcement supporting #75, #111 and #119. */
class AuthInterceptorUnitTests {
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        return request;
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void matchingRoleCanContinue(Role role) throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        request.getSession().setAttribute(LoginSession.CURRENT_USER,
                new SessionUser(1L, "Person", "person@example.test", role));
        assertThat(new AuthInterceptor(new LoginSession(), role, "/login").preHandle(request, response, new Object()))
                .isTrue();
        assertThat(response.getRedirectedUrl()).isNull();
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void missingSessionIsRedirected(Role role) throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        assertThat(new AuthInterceptor(new LoginSession(), role, "/login").preHandle(request, response, new Object()))
                .isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
    }
    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void wrongRoleIsRedirected(Role requiredRole) throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        Role actualRole = requiredRole == Role.CUSTOMER ? Role.ADMIN : Role.CUSTOMER;
        request.getSession().setAttribute(LoginSession.CURRENT_USER,
                new SessionUser(1L, "Person", "person@example.test", actualRole));
        assertThat(new AuthInterceptor(new LoginSession(), requiredRole, "/login").preHandle(request, response, new Object()))
                .isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
    }
}
