package com.csit314.platform.service;

import com.csit314.platform.model.*;
import com.csit314.platform.repository.FailedLoginAttemptRepository;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #75 and #111; customer authentication also supports #119. */
@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {
    @Mock UserAccountRepository users;
    @Mock FailedLoginAttemptRepository failedLoginAttempts;
    @Mock PasswordEncoder encoder;
    AuthService service;
    @BeforeEach void setUp() { service = new AuthService(users, failedLoginAttempts, encoder); }

    private UserAccount account(Role role) {
        UserAccount result = switch (role) {
            case ADMIN -> new Admin("Admin", "person@example.test", "encoded");
            case CUSTOMER -> new Customer("Customer", "person@example.test", "encoded");
            case INTERIOR_DESIGNER -> new InteriorDesigner("Designer", "person@example.test", "encoded",
                    new Company("Studio", "Description", "12345"));
            default -> throw new IllegalArgumentException();
        };
        result.setStatus(AccountStatus.ACTIVE);
        return result;
    }
    private UserAccount login(Role role, String email, String password) {
        return switch (role) {
            case ADMIN -> service.loginAdmin(email, password, "127.0.0.1");
            case CUSTOMER -> service.loginCustomer(email, password, "127.0.0.1");
            case INTERIOR_DESIGNER -> service.loginDesigner(email, password, "127.0.0.1");
            default -> throw new IllegalArgumentException();
        };
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void activeAccountAuthenticatesAndNormalisesEmail(Role role) {
        UserAccount user = account(role);
        when(users.findByEmail("person@example.test")).thenReturn(Optional.of(user));
        when(encoder.matches("valid-password", "encoded")).thenReturn(true);
        assertThat(login(role, " Person@Example.Test ", "valid-password")).isSameAs(user);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void unknownAccountIsRejectedWithoutPasswordComparison(Role role) {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> login(role, "missing@example.test", "password123"))
                .isInstanceOf(ServiceException.class).hasMessage("Invalid email or password.");
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void wrongPasswordDoesNotUpdateLastLogin(Role role) {
        UserAccount user = account(role);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> login(role, user.getEmail(), "wrong"))
                .isInstanceOf(ServiceException.class).hasMessage("Invalid email or password.");
        assertThat(user.getLastLoginAt()).isNull();
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void nullPasswordIsRejectedWithoutCallingEncoder(Role role) {
        UserAccount user = account(role);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> login(role, user.getEmail(), null)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(encoder);
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = {"ADMIN", "CUSTOMER", "INTERIOR_DESIGNER"})
    void credentialsFromAnotherRoleCannotUsePortal(Role expectedRole) {
        for (Role actualRole : new Role[]{Role.ADMIN, Role.CUSTOMER, Role.INTERIOR_DESIGNER}) {
            if (actualRole == expectedRole) continue;
            UserAccount user = account(actualRole);
            when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(encoder.matches("password123", "encoded")).thenReturn(true);
            assertThatThrownBy(() -> login(expectedRole, user.getEmail(), "password123"))
                    .isInstanceOf(ServiceException.class).hasMessage("Invalid email or password.");
            assertThat(user.getLastLoginAt()).isNull();
        }
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void nonActiveDesignerCannotAuthenticate(AccountStatus state) {
        UserAccount user = account(Role.INTERIOR_DESIGNER);
        user.setStatus(state);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "encoded")).thenReturn(true);
        assertThatThrownBy(() -> service.loginDesigner(user.getEmail(), "password123", "127.0.0.1"))
                .isInstanceOf(ServiceException.class);
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test void rejectedDesignerReceivesRecordedReason() {
        InteriorDesigner user = (InteriorDesigner) account(Role.INTERIOR_DESIGNER);
        user.reject("Company details missing");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "encoded")).thenReturn(true);
        assertThatThrownBy(() -> service.loginDesigner(user.getEmail(), "password123", "127.0.0.1"))
                .hasMessageContaining("Company details missing");
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   "})
    void missingEmailIsHandledWithoutUnexpectedException(String email) {
        when(users.findByEmail("")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loginAdmin(email, "password123", "127.0.0.1")).isInstanceOf(ServiceException.class);
    }

    // ADM-13 - these saves must happen even though the caller throws right after login()
    // returns; this guards against a @Transactional method rolling them back again
    @Test void aFailedAttemptIsSavedAndTheAccountsCounterIsPersistedExplicitly() {
        UserAccount user = account(Role.CUSTOMER);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.loginCustomer(user.getEmail(), "wrong", "127.0.0.1"))
                .isInstanceOf(ServiceException.class);
        verify(failedLoginAttempts).save(any());
        verify(users).save(user);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test void aSuccessfulLoginIsSavedExplicitly() {
        UserAccount user = account(Role.CUSTOMER);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("valid-password", "encoded")).thenReturn(true);
        service.loginCustomer(user.getEmail(), "valid-password", "127.0.0.1");
        verify(users).save(user);
    }
}
