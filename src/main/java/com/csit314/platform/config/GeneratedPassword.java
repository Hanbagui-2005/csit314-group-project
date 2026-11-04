package com.csit314.platform.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;

// a fixed, publicly-documented default password is a real risk if someone deploys without
// overriding it - generate a random one instead whenever the env var isn't set
final class GeneratedPassword {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private GeneratedPassword() {
    }

    static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return password.toString();
    }

    // Anthony's review - AdminAccountInitializer and PlatformManagerAccountInitializer
    // had nearly identical generate-and-announce blocks; this is the one place that
    // decides what password to actually use and, if one had to be generated, how it's
    // surfaced - a change to that (e.g. a different way of storing it) only has to be
    // made here.
    //
    // Printing the generated password to stdout doesn't actually keep it out of a
    // persisted log store either: under Docker, systemd/journald and most PaaS log
    // shippers, stdout *is* the log stream. Writing it to a local file instead (and
    // restricting that file to the owner where the filesystem supports it) is the only
    // one of his suggested options that doesn't need an extra deployment-specific
    // decision (a mandatory env var would break the simple "just run it" dev setup)
    static String resolveAndAnnounce(String configuredPassword, Logger log, String roleLabel, String envVarName,
                                     String fileName, Path secretsDir) {
        if (!configuredPassword.isBlank()) {
            return configuredPassword;
        }
        String generated = generate();
        Path file = writeToSecretsFile(secretsDir, fileName, generated);
        log.warn("No {} set - a random password was generated for the default {} account. "
                + "It was written to {} (not this log) - read it from there, then delete the file.",
                envVarName, roleLabel, file.toAbsolutePath());
        return generated;
    }

    private static Path writeToSecretsFile(Path secretsDir, String fileName, String password) {
        try {
            Files.createDirectories(secretsDir);
            Path file = secretsDir.resolve(fileName);
            Files.writeString(file, password + System.lineSeparator());
            restrictToOwnerIfSupported(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // POSIX permissions aren't supported on every filesystem (notably not on Windows,
    // which this project is often developed on) - best-effort only, never blocks startup
    private static void restrictToOwnerIfSupported(Path file) throws IOException {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
