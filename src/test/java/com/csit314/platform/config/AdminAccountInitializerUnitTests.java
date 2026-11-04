package com.csit314.platform.config;

import com.csit314.platform.model.Admin;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerUnitTests {

    @Mock UserAccountRepository userAccountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserAccount existingAdmin;

    @Test
    void doesNothingWhenAnAdminAlreadyExistsAndItsPasswordIsNotTheOldDefault(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(existingAdmin.getPasswordHash()).thenReturn("hashed");
        when(passwordEncoder.matches("Admin@12345", "hashed")).thenReturn(false);
        AdminAccountInitializer initializer = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "Admin@12345", secretsDir.toString());

        initializer.run(null);

        verify(userAccountRepository, never()).save(any());
    }

    // Anthony's review - removing the fixed fallback only protects a fresh database;
    // an instance already seeded before that change keeps using the old, now-public
    // default forever unless something flags it
    @Test
    void warnsWhenAnExistingAdminStillUsesTheOldHardcodedDefaultPassword(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.of(existingAdmin));
        when(existingAdmin.getEmail()).thenReturn("admin@platform.local");
        when(existingAdmin.getPasswordHash()).thenReturn("hash-of-the-old-default");
        when(passwordEncoder.matches("Admin@12345", "hash-of-the-old-default")).thenReturn(true);
        AdminAccountInitializer initializer = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "", secretsDir.toString());

        initializer.run(null);

        verify(passwordEncoder).matches("Admin@12345", "hash-of-the-old-default");
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void usesTheConfiguredPasswordWhenOneIsSet(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@12345")).thenReturn("hashed");
        AdminAccountInitializer initializer = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "Admin@12345", secretsDir.toString());

        initializer.run(null);

        verify(passwordEncoder).encode("Admin@12345");
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(userAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    // a fixed, publicly-documented fallback password is a real risk if ADMIN_PASSWORD is
    // never set in production - this must generate a random one instead
    @Test
    void generatesARandomPasswordWhenNoneIsConfigured(@TempDir Path secretsDir) throws Exception {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        AdminAccountInitializer initializer = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "", secretsDir.toString());

        initializer.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).isNotBlank();
        assertThat(passwordCaptor.getValue()).isNotEqualTo("Admin@12345");
        verify(userAccountRepository).save(any(Admin.class));
    }

    // Anthony's review - stdout isn't actually outside the log store under
    // Docker/systemd/most PaaS setups, so the generated password goes to a file instead
    @Test
    void writesTheGeneratedPasswordToAFileInsteadOfStdout(@TempDir Path secretsDir) throws Exception {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        AdminAccountInitializer initializer = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "", secretsDir.toString());

        initializer.run(null);

        Path file = secretsDir.resolve("admin-password.txt");
        assertThat(file).exists();
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(Files.readString(file)).contains(passwordCaptor.getValue());
    }

    @Test
    void generatesADifferentPasswordEachTime(@TempDir Path secretsDir) {
        when(userAccountRepository.findFirstByRole(Role.ADMIN)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        AdminAccountInitializer first = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "", secretsDir.toString());
        AdminAccountInitializer second = new AdminAccountInitializer(userAccountRepository, passwordEncoder,
                "Admin", "admin@platform.local", "", secretsDir.toString());

        first.run(null);
        second.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getAllValues().get(0)).isNotEqualTo(passwordCaptor.getAllValues().get(1));
    }
}
