package com.csit314.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class GeneratedPasswordUnitTests {

    private static final Logger log = LoggerFactory.getLogger(GeneratedPasswordUnitTests.class);

    @Test
    void generatesATwentyCharacterPassword() {
        assertThat(GeneratedPassword.generate()).hasSize(20);
    }

    @Test
    void generatesADifferentPasswordEachCall() {
        assertThat(GeneratedPassword.generate()).isNotEqualTo(GeneratedPassword.generate());
    }

    @Test
    void resolveAndAnnounceReturnsTheConfiguredPasswordUnchangedWhenOneIsSet(@TempDir Path secretsDir) {
        String result = GeneratedPassword.resolveAndAnnounce("MyPass@123", log, "admin", "ADMIN_PASSWORD",
                "admin-password.txt", secretsDir);

        assertThat(result).isEqualTo("MyPass@123");
        assertThat(Files.exists(secretsDir.resolve("admin-password.txt"))).isFalse();
    }

    // Anthony's review - the generated password goes to a file, not stdout, since
    // stdout is itself the persisted log stream under Docker/systemd/most PaaS setups
    @Test
    void resolveAndAnnounceWritesAGeneratedPasswordToTheSecretsFile(@TempDir Path secretsDir) throws Exception {
        String result = GeneratedPassword.resolveAndAnnounce("", log, "admin", "ADMIN_PASSWORD",
                "admin-password.txt", secretsDir);

        assertThat(result).isNotBlank();
        Path file = secretsDir.resolve("admin-password.txt");
        assertThat(Files.readString(file)).contains(result);
    }

    @Test
    void resolveAndAnnounceCreatesTheSecretsDirectoryIfItDoesNotExistYet(@TempDir Path tempDir) {
        Path secretsDir = tempDir.resolve("does-not-exist-yet");

        GeneratedPassword.resolveAndAnnounce("", log, "admin", "ADMIN_PASSWORD", "admin-password.txt", secretsDir);

        assertThat(Files.isDirectory(secretsDir)).isTrue();
    }
}
