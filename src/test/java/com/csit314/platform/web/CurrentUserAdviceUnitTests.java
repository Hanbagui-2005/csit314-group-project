package com.csit314.platform.web;

import com.csit314.platform.model.Role;
import com.csit314.platform.service.UserManagementService;
import com.csit314.platform.web.session.LoginSession;
import com.csit314.platform.web.session.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// ADM-12 - a waiting registration shouldn't need the admin to happen to be on /admin/users
// to notice it; this is what puts the count on every admin page via the navbar
@ExtendWith(MockitoExtension.class)
class CurrentUserAdviceUnitTests {

    @Mock LoginSession loginSession;
    @Mock UserManagementService userManagementService;
    @Mock HttpServletRequest request;
    CurrentUserAdvice advice;

    @Test
    void showsThePendingCountForAnAdmin() {
        advice = new CurrentUserAdvice(loginSession, userManagementService);
        SessionUser admin = new SessionUser(1L, "Admin", "admin@example.test", Role.ADMIN);
        when(loginSession.current(request)).thenReturn(Optional.of(admin));
        when(userManagementService.pendingDesignerCount()).thenReturn(3L);

        assertThat(advice.pendingDesignerNotificationCount(request)).isEqualTo(3L);
    }

    @Test
    void isNullForANonAdmin() {
        advice = new CurrentUserAdvice(loginSession, userManagementService);
        SessionUser customer = new SessionUser(2L, "Customer", "customer@example.test", Role.CUSTOMER);
        when(loginSession.current(request)).thenReturn(Optional.of(customer));

        assertThat(advice.pendingDesignerNotificationCount(request)).isNull();
        verifyNoInteractions(userManagementService);
    }

    @Test
    void isNullWhenLoggedOut() {
        advice = new CurrentUserAdvice(loginSession, userManagementService);
        when(loginSession.current(request)).thenReturn(Optional.empty());

        assertThat(advice.pendingDesignerNotificationCount(request)).isNull();
        verifyNoInteractions(userManagementService);
    }
}
