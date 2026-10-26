package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.PasswordResetToken;
import com.csit314.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Taiga #123. No actual email delivery is claimed. */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceUnitTests {
    @Mock UserAccountRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock PasswordEncoder encoder;
    PasswordResetService service;
    Customer customer;
    @BeforeEach void setUp() {
        service = new PasswordResetService(users, tokens, encoder, 30, "http://localhost");
        customer = new Customer("Client", "client@example.test", "old-hash");
    }
    private PasswordResetToken usable() {
        return new PasswordResetToken(customer, "token-hash", LocalDateTime.now().plusHours(1));
    }
    @Test void unknownEmailCreatesNoResetToken() {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        assertThat(service.requestReset(" MISSING@EXAMPLE.TEST ")).isEmpty();
        verifyNoInteractions(tokens, encoder);
    }
    @Test void newRequestStoresTokenHashAndInvalidatesOldLinks() {
        PasswordResetToken old = usable();
        when(users.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(tokens.findByUserAndUsedAtIsNull(customer)).thenReturn(List.of(old));
        var before = LocalDateTime.now();
        String raw = service.requestReset(customer.getEmail()).orElseThrow();
        var capture = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(capture.capture());
        var saved = capture.getValue();
        assertThat(raw).hasSize(43);
        assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(saved.getUser()).isSameAs(customer);
        assertThat(saved.getExpiresAt()).isAfterOrEqualTo(before.plusMinutes(30))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(30));
        assertThat(old.getUsedAt()).isNotNull();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"  "})
    void missingTokenIsInvalidWithoutQuery(String raw) {
        assertThat(service.isTokenValid(raw)).isFalse();
        verifyNoInteractions(tokens);
    }
    @Test void unknownTokenIsInvalid() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThat(service.isTokenValid("unknown")).isFalse();
    }
    @Test void expiredTokenCannotChangePassword() {
        var token = new PasswordResetToken(customer, "hash", LocalDateTime.now().minusMinutes(1));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.resetPassword("raw", "password123", "password123"))
                .hasMessageContaining("invalid or has expired");
        assertThat(customer.getPasswordHash()).isEqualTo("old-hash");
        verifyNoInteractions(encoder);
    }
    @Test void usedTokenCannotChangePassword() {
        var token = usable(); token.markUsed();
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        assertThat(service.isTokenValid("raw")).isFalse();
        assertThatThrownBy(() -> service.resetPassword("raw", "password123", "password123"))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(encoder);
    }
    @Test void matchingValidPasswordIsEncodedAndTokenConsumedOnce() {
        var token = usable();
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(encoder.encode("password123")).thenReturn("new-hash");
        service.resetPassword("raw", "password123", "password123");
        assertThat(customer.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        assertThatThrownBy(() -> service.resetPassword("raw", "password123", "password123"))
                .isInstanceOf(ServiceException.class);
        verify(encoder, times(1)).encode("password123");
    }
    @Test void invalidNewPasswordsLeaveAccountAndTokenUnchanged() {
        var token = usable();
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        for (String[] pair : new String[][]{{"short", "short"}, {"password123", "different"}}) {
            assertThatThrownBy(() -> service.resetPassword("raw", pair[0], pair[1]))
                    .isInstanceOf(ServiceException.class);
        }
        assertThat(customer.getPasswordHash()).isEqualTo("old-hash");
        assertThat(token.getUsedAt()).isNull();
        verifyNoInteractions(encoder);
    }
    @Test void minimumLengthPasswordIsAccepted() {
        var token = usable();
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(encoder.encode("1234567a")).thenReturn("boundary-hash");
        service.resetPassword("raw", "1234567a", "1234567a");
        assertThat(customer.getPasswordHash()).isEqualTo("boundary-hash");
    }
}
