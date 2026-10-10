package com.csit314.platform.service;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// checks credentials for the admin, designer and customer login forms
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ADM-01
    @Transactional
    public UserAccount loginAdmin(String email, String password) {
        return login(email, password, Role.ADMIN);
    }

    // IDR-02
    @Transactional
    public UserAccount loginDesigner(String email, String password) {
        return login(email, password, Role.INTERIOR_DESIGNER);
    }

    // CUS-02
    @Transactional
    public UserAccount loginCustomer(String email, String password) {
        return login(email, password, Role.CUSTOMER);
    }

    private UserAccount login(String email, String password, Role expectedRole) {
        UserAccount user = userAccountRepository.findByEmail(InputRules.normaliseEmail(email))
                .orElseThrow(() -> new ServiceException(INVALID_CREDENTIALS));

        // same message for a wrong password or wrong portal so we don't leak which emails exist
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())
                || user.getRole() != expectedRole) {
            throw new ServiceException(INVALID_CREDENTIALS);
        }

        switch (user.getStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw new ServiceException(
                    "Your registration is still being reviewed. You can log in once an admin approves it.");
            case REJECTED -> throw new ServiceException(rejectedMessage(user));
            case SUSPENDED -> throw new ServiceException("This account has been suspended.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        return user;
    }

    private String rejectedMessage(UserAccount user) {
        String message = "Your registration was not approved.";
        if (user instanceof InteriorDesigner designer && designer.getRejectionReason() != null) {
            message += " Reason: " + designer.getRejectionReason();
        }
        return message;
    }
}
