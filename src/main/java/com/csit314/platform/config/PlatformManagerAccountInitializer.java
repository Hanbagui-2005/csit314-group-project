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

// PM-01 - there's no public sign-up for this role either, so seed the first one from config
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
                                              @Value("${app.platform-manager.password}") String password) {
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
        userAccountRepository.save(new PlatformManager(name, email, passwordEncoder.encode(password)));
        log.info("Created default platform manager account {}", email);
    }
}
