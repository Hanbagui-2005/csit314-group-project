package com.csit314.platform.config;

import com.csit314.platform.model.Admin;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(userAccountRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userAccountRepository, passwordEncoder, "Admin", "admin@platform.local", "Admin@12345");

        initializer.run(null);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void usesTheConfiguredPasswordWhenOneIsSet() {
        when(userAccountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode("Admin@12345")).thenReturn("hashed");
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userAccountRepository, passwordEncoder, "Admin", "admin@platform.local", "Admin@12345");

        initializer.run(null);

        verify(passwordEncoder).encode("Admin@12345");
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    // a fixed, publicly-documented fallback password is a real risk if ADMIN_PASSWORD is
    // never set in production - this must generate a random one instead
    @Test
    void generatesARandomPasswordWhenNoneIsConfigured() {
        when(userAccountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        AdminAccountInitializer initializer = new AdminAccountInitializer(
                userAccountRepository, passwordEncoder, "Admin", "admin@platform.local", "");

        initializer.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).isNotBlank();
        assertThat(passwordCaptor.getValue()).isNotEqualTo("Admin@12345");
        verify(userAccountRepository).save(any(Admin.class));
    }

    @Test
    void generatesADifferentPasswordEachTime() {
        when(userAccountRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        AdminAccountInitializer first = new AdminAccountInitializer(
                userAccountRepository, passwordEncoder, "Admin", "admin@platform.local", "");
        AdminAccountInitializer second = new AdminAccountInitializer(
                userAccountRepository, passwordEncoder, "Admin", "admin@platform.local", "");

        first.run(null);
        second.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getAllValues().get(0)).isNotEqualTo(passwordCaptor.getAllValues().get(1));
    }
}
