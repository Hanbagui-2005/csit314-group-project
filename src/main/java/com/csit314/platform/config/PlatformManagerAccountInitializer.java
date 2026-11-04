package com.csit314.platform.config;

import com.csit314.platform.model.PlatformManager;
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

// there's no public sign-up for this role either, so seed the first one from config
// (unlike ADM-01, login itself isn't a separately numbered Taiga story for this role)
@Component
public class PlatformManagerAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformManagerAccountInitializer.class);

    // Anthony's review - same reasoning as AdminAccountInitializer: kept only to flag
    // an account seeded by an older version of this app, never used as a fallback again
    private static final String OLD_HARDCODED_DEFAULT_PASSWORD = "Manager@12345";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;
    private final Path secretsDir;

    public PlatformManagerAccountInitializer(UserAccountRepository userAccountRepository,
                                              PasswordEncoder passwordEncoder,
                                              @Value("${app.platform-manager.name}") String name,
                                              @Value("${app.platform-manager.email}") String email,
                                              @Value("${app.platform-manager.password:}") String password,
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
        Optional<UserAccount> existingManager = userAccountRepository.findFirstByRole(Role.PLATFORM_MANAGER);
        if (existingManager.isPresent()) {
            // Anthony's review - same reasoning as AdminAccountInitializer: this only
            // protects a fresh database, so flag an already-seeded account that's
            // still using the old, now-public default instead of silently trusting it
            warnIfStillUsingTheOldDefaultPassword(existingManager.get());
            return;
        }
        String actualPassword = GeneratedPassword.resolveAndAnnounce(password, log, "platform manager",
                "PLATFORM_MANAGER_PASSWORD", "platform-manager-password.txt", secretsDir);
        userAccountRepository.save(new PlatformManager(name, email, passwordEncoder.encode(actualPassword)));
        log.info("Created default platform manager account {}", email);
    }

    private void warnIfStillUsingTheOldDefaultPassword(UserAccount manager) {
        if (passwordEncoder.matches(OLD_HARDCODED_DEFAULT_PASSWORD, manager.getPasswordHash())) {
            log.warn("SECURITY WARNING: the platform manager account {} is still using the old "
                    + "publicly-documented default password. Log in and change it immediately.", manager.getEmail());
        }
    }
}
