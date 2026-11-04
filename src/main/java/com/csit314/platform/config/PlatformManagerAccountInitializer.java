package com.csit314.platform.config;

import com.csit314.platform.model.PlatformManager;
import com.csit314.platform.model.Role;
import com.csit314.platform.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

// there's no public sign-up for this role either, so seed the first one from config
// (unlike ADM-01, login itself isn't a separately numbered Taiga story for this role)
@Component
public class PlatformManagerAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformManagerAccountInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public PlatformManagerAccountInitializer(UserAccountRepository userAccountRepository,
                                              PasswordEncoder passwordEncoder,
                                              @Value("${app.platform-manager.name}") String name,
                                              @Value("${app.platform-manager.email}") String email,
                                              @Value("${app.platform-manager.password:}") String password) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.existsByRole(Role.PLATFORM_MANAGER)) {
            return;
        }
        // same reasoning as AdminAccountInitializer - don't fall back to a fixed,
        // publicly-documented password when PLATFORM_MANAGER_PASSWORD isn't set
        String actualPassword = password.isBlank() ? GeneratedPassword.generate() : password;
        userAccountRepository.save(new PlatformManager(name, email, passwordEncoder.encode(actualPassword)));
        if (password.isBlank()) {
            log.warn("No PLATFORM_MANAGER_PASSWORD set - created default platform manager account {} "
                    + "with generated password: {}", email, actualPassword);
        } else {
            log.info("Created default platform manager account {}", email);
        }
    }
}
