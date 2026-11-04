package com.csit314.platform.web.session;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP role enforcement supporting #75, #111 and #119, plus ADM-06/08's immediate logout on suspension. */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorUnitTests {

    @Mock UserAccountRepository userAccountRepository;

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
        when(userAccountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(true);

        assertThat(new AuthInterceptor(new LoginSession(), role, "/login", userAccountRepository)
                .preHandle(request, response, new Object())).isTrue();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void missingSessionIsRedirected(Role role) throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        assertThat(new AuthInterceptor(new LoginSession(), role, "/login", userAccountRepository)
                .preHandle(request, response, new Object())).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
        verifyNoInteractions(userAccountRepository);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void wrongRoleIsRedirected(Role requiredRole) throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        Role actualRole = requiredRole == Role.CUSTOMER ? Role.ADMIN : Role.CUSTOMER;
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(LoginSession.CURRENT_USER,
                new SessionUser(1L, "Person", "person@example.test", actualRole));

        assertThat(new AuthInterceptor(new LoginSession(), requiredRole, "/login", userAccountRepository)
                .preHandle(request, response, new Object())).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
        // the role mismatch is decided before any database lookup
        verifyNoInteractions(userAccountRepository);
        // a user with the wrong role for this route (e.g. a designer clicking a
        // customer-only link) is just redirected - their whole session stays valid
        assertThat(session.isInvalid()).isFalse();
    }

    // ADM-06/08 - a suspended account's existing session is rejected and cleared on its very
    // next request, instead of staying valid until the session naturally expires
    @Test
    void aSuspendedAccountsSessionIsRejectedAndCleared() throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(LoginSession.CURRENT_USER,
                new SessionUser(1L, "Person", "person@example.test", Role.CUSTOMER));
        when(userAccountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(false);

        boolean allowed = new AuthInterceptor(new LoginSession(), Role.CUSTOMER, "/login", userAccountRepository)
                .preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/login");
        assertThat(session.isInvalid()).isTrue();
    }

    // the account was deleted outright after the session was created
    @Test
    void aDeletedAccountsSessionIsRejected() throws Exception {
        var request = request(); var response = new MockHttpServletResponse();
        request.getSession().setAttribute(LoginSession.CURRENT_USER,
                new SessionUser(1L, "Person", "person@example.test", Role.CUSTOMER));
        when(userAccountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(false);

        boolean allowed = new AuthInterceptor(new LoginSession(), Role.CUSTOMER, "/login", userAccountRepository)
                .preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
    }
}
