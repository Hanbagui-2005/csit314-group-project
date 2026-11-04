package com.csit314.platform.config;

import com.csit314.platform.model.PlatformManager;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformManagerAccountInitializerUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserAccount existingManager;

    @Test
    void doesNothingWhenAPlatformManagerAlreadyExistsAndItsPasswordIsNotTheOldDefault(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.PLATFORM_MANAGER)).thenReturn(Optional.of(existingManager));
        when(existingManager.getPasswordHash()).thenReturn("hashed");
        when(passwordEncoder.matches("Manager@12345", "hashed")).thenReturn(false);
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(userAccountRepository,
                passwordEncoder, "Manager", "platform-manager@platform.local", "Manager@12345", secretsDir.toString());

        initializer.run(null);

        verify(userAccountRepository, never()).save(any());
    }

    // Anthony's review - same reasoning as AdminAccountInitializer
    @Test
    void warnsWhenAnExistingManagerStillUsesTheOldHardcodedDefaultPassword(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.PLATFORM_MANAGER)).thenReturn(Optional.of(existingManager));
        when(existingManager.getEmail()).thenReturn("platform-manager@platform.local");
        when(existingManager.getPasswordHash()).thenReturn("hash-of-the-old-default");
        when(passwordEncoder.matches("Manager@12345", "hash-of-the-old-default")).thenReturn(true);
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(userAccountRepository,
                passwordEncoder, "Manager", "platform-manager@platform.local", "", secretsDir.toString());

        initializer.run(null);

        verify(passwordEncoder).matches("Manager@12345", "hash-of-the-old-default");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void usesTheConfiguredPasswordWhenOneIsSet(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.PLATFORM_MANAGER)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Manager@12345")).thenReturn("hashed");
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(userAccountRepository,
                passwordEncoder, "Manager", "platform-manager@platform.local", "Manager@12345", secretsDir.toString());

        initializer.run(null);

        verify(passwordEncoder).encode("Manager@12345");
        ArgumentCaptor<PlatformManager> captor = ArgumentCaptor.forClass(PlatformManager.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    // same reasoning as AdminAccountInitializerUnitTests - no fixed fallback password
    @Test
    void generatesARandomPasswordWhenNoneIsConfigured(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.PLATFORM_MANAGER)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        PlatformManagerAccountInitializer initializer = new PlatformManagerAccountInitializer(userAccountRepository,
                passwordEncoder, "Manager", "platform-manager@platform.local", "", secretsDir.toString());

        initializer.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).isNotBlank();
        assertThat(passwordCaptor.getValue()).isNotEqualTo("Manager@12345");
        verify(userAccountRepository).save(any(PlatformManager.class));
    }
}
