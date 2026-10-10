package com.csit314.platform.web.session;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;

/** Taiga #119: actual session state, rotation, logout and isolation. */
class LoginSessionUnitTests {
    private final LoginSession sessions = new LoginSession();
    @Test void missingSessionDoesNotCreateOne() {
        var request = new MockHttpServletRequest();
        assertThat(sessions.current(request)).isEmpty();
        assertThat(request.getSession(false)).isNull();
    }
    @Test void loginRotatesSessionIdAndStoresCustomerSnapshot() {
        var request = new MockHttpServletRequest();
        String oldId = request.getSession(true).getId();
        var user = sessions.start(request, new Customer("Client", "client@example.test", "secret-hash"));
        assertThat(request.getSession().getId()).isNotEqualTo(oldId);
        assertThat(sessions.current(request)).contains(user);
        assertThat(user.hasRole(Role.CUSTOMER)).isTrue();
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
        assertThat(user.toString()).doesNotContain("secret-hash");
    }
    @Test void logoutInvalidatesExistingSession() {
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession(); request.setSession(session);
        sessions.start(request, new Customer("Client", "client@example.test", "hash"));
        sessions.end(request);
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void logoutWithoutSessionIsSafe() {
        var request = new MockHttpServletRequest();
        assertThatCode(() -> sessions.end(request)).doesNotThrowAnyException();
        assertThat(request.getSession(false)).isNull();
    }
    @Test void invalidatedSessionIsNotRecognisedAsAuthenticated() {
        var request = new MockHttpServletRequest();
        sessions.start(request, new Customer("Client", "client@example.test", "hash"));
        request.getSession().invalidate();
        assertThat(sessions.current(request)).isEmpty();
    }
    @Test void differentRequestsDoNotShareCustomers() {
        var first = new MockHttpServletRequest(); var second = new MockHttpServletRequest();
        sessions.start(first, new Customer("First", "first@example.test", "hash"));
        sessions.start(second, new Customer("Second", "second@example.test", "hash"));
        assertThat(sessions.current(first).orElseThrow().email()).isEqualTo("first@example.test");
        assertThat(sessions.current(second).orElseThrow().email()).isEqualTo("second@example.test");
        assertThat(first.getSession()).isNotSameAs(second.getSession());
    }
}
