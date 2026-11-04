package com.csit314.platform.config;

import com.csit314.platform.model.PlatformManager;
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
class PlatformManagerAccountInitializerUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void doesNothingWhenAPlatformManagerAlreadyExists() {
        when(userAccountRepository.existsByRole(Role.PLATFORM_MANAGER)).thenReturn(true);
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(
                userAccountRepository, passwordEncoder, "Manager", "platform-manager@platform.local", "Manager@12345");

        initializer.run(null);

        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void usesTheConfiguredPasswordWhenOneIsSet() {
        when(userAccountRepository.existsByRole(Role.PLATFORM_MANAGER)).thenReturn(false);
        when(passwordEncoder.encode("Manager@12345")).thenReturn("hashed");
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(
                userAccountRepository, passwordEncoder, "Manager", "platform-manager@platform.local", "Manager@12345");

        initializer.run(null);

        verify(passwordEncoder).encode("Manager@12345");
        ArgumentCaptor<PlatformManager> captor = ArgumentCaptor.forClass(PlatformManager.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    // same reasoning as AdminAccountInitializerUnitTests - no fixed fallback password
    @Test
    void generatesARandomPasswordWhenNoneIsConfigured() {
        when(userAccountRepository.existsByRole(Role.PLATFORM_MANAGER)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(
                userAccountRepository, passwordEncoder, "Manager", "platform-manager@platform.local", "");

        initializer.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).isNotBlank();
        assertThat(passwordCaptor.getValue()).isNotEqualTo("Manager@12345");
        verify(userAccountRepository).save(any(PlatformManager.class));
    }
}
