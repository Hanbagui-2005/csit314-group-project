package com.csit314.platform.config;

import com.csit314.platform.model.Admin;
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

// there's no public admin sign-up, so create the first User Admin from config on startup
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminAccountInitializer(UserAccountRepository userAccountRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.name}") String name,
                                   @Value("${app.admin.email}") String email,
                                   @Value("${app.admin.password}") String password) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        userAccountRepository.save(new Admin(name, email, passwordEncoder.encode(password)));
        log.info("Created default admin account {}", email);
    }
}
