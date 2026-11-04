package com.csit314.platform.config;

import com.csit314.platform.model.Admin;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

// there's no public admin sign-up, so create the first User Admin from config on startup
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    // Anthony's review - the password that used to be hardcoded here before the fixed
    // fallback was removed. Kept only so an account seeded by an older version of this
    // app can be detected and flagged, never used as a fallback again
    private static final String OLD_HARDCODED_DEFAULT_PASSWORD = "Admin@12345";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;
    private final Path secretsDir;

    public AdminAccountInitializer(UserAccountRepository userAccountRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.name}") String name,
                                   @Value("${app.admin.email}") String email,
                                   @Value("${app.admin.password:}") String password,
                                   @Value("${app.secrets-dir:secrets}") String secretsDir) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
        this.secretsDir = Path.of(secretsDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        Optional<UserAccount> existingAdmin = userAccountRepository.findFirstByRole(Role.ADMIN);
        if (existingAdmin.isPresent()) {
            // Anthony's review - removing the fixed fallback only protects a fresh
            // database. An instance that was already running before this change has an
            // admin account whose password hash may still match the old, now-public
            // "Admin@12345" default - nothing checked for that before
            warnIfStillUsingTheOldDefaultPassword(existingAdmin.get());
            return;
        }
        String actualPassword = GeneratedPassword.resolveAndAnnounce(password, log, "admin", "ADMIN_PASSWORD",
                "admin-password.txt", secretsDir);
        userAccountRepository.save(new Admin(name, email, passwordEncoder.encode(actualPassword)));
        log.info("Created default admin account {}", email);
    }

    private void warnIfStillUsingTheOldDefaultPassword(UserAccount admin) {
        if (passwordEncoder.matches(OLD_HARDCODED_DEFAULT_PASSWORD, admin.getPasswordHash())) {
            log.warn("SECURITY WARNING: the admin account {} is still using the old publicly-documented "
                    + "default password. Log in and change it immediately.", admin.getEmail());
        }
    }
}
