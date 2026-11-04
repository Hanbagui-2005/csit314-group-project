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
                                   @Value("${app.admin.password:}") String password) {
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
        // a fixed, publicly-documented fallback password would be a real risk if
        // ADMIN_PASSWORD is never set, so generate one instead and only print it once
        String actualPassword = password.isBlank() ? GeneratedPassword.generate() : password;
        userAccountRepository.save(new Admin(name, email, passwordEncoder.encode(actualPassword)));
        if (password.isBlank()) {
            // Anthony's review: the app log usually ends up in files, log shipping or
            // aggregators that more people can read, and never expires - so the
            // password itself goes straight to stdout instead, not through SLF4J. The
            // log line says it happened (useful for anyone grepping logs later) without
            // including the secret itself
            log.warn("No ADMIN_PASSWORD set - a random password was generated for {}. "
                    + "It is printed once below, not in this log.", email);
            System.out.println("================================================================");
            System.out.println("Generated password for default admin account " + email + ":");
            System.out.println(actualPassword);
            System.out.println("This is shown once and is not stored anywhere else - copy it now.");
            System.out.println("Set ADMIN_PASSWORD to choose your own instead.");
            System.out.println("================================================================");
        } else {
            log.info("Created default admin account {}", email);
        }
    }
}
