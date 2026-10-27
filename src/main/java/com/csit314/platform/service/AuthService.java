package com.csit314.platform.service;

import com.csit314.platform.model.FailedLoginAttempt;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.FailedLoginAttemptRepository;
import com.csit314.platform.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// checks credentials for the admin, designer and customer login forms
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private static final String LOCKED_MESSAGE =
            "Too many failed login attempts. Ask an admin to unlock your account.";

    private final UserAccountRepository userAccountRepository;
    private final FailedLoginAttemptRepository failedLoginAttemptRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository,
                       FailedLoginAttemptRepository failedLoginAttemptRepository,
                       PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.failedLoginAttemptRepository = failedLoginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ADM-01 - deliberately not @Transactional: a failed attempt below must survive even
    // though this method immediately throws, which would otherwise roll that write back
    public UserAccount loginAdmin(String email, String password, String ipAddress) {
        return login(email, password, Role.ADMIN, ipAddress);
    }

    // IDR-02
    public UserAccount loginDesigner(String email, String password, String ipAddress) {
        return login(email, password, Role.INTERIOR_DESIGNER, ipAddress);
    }

    // CUS-02
    public UserAccount loginCustomer(String email, String password, String ipAddress) {
        return login(email, password, Role.CUSTOMER, ipAddress);
    }

    private UserAccount login(String email, String password, Role expectedRole, String ipAddress) {
        String normalisedEmail = InputRules.normaliseEmail(email);
        Optional<UserAccount> maybeUser = userAccountRepository.findByEmail(normalisedEmail);

        // ADM-09/13 - locked accounts are rejected before even checking the password
        if (maybeUser.isPresent() && maybeUser.get().isLocked()) {
            throw new ServiceException(LOCKED_MESSAGE);
        }

        UserAccount user = maybeUser.orElse(null);
        if (user == null || password == null || !passwordEncoder.matches(password, user.getPasswordHash())
                || user.getRole() != expectedRole) {
            recordFailedAttempt(normalisedEmail, ipAddress, user);
            throw new ServiceException(INVALID_CREDENTIALS);
        }

        switch (user.getStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw new ServiceException(
                    "Your registration is still being reviewed. You can log in once an admin approves it.");
            case REJECTED -> throw new ServiceException(rejectedMessage(user));
            case SUSPENDED -> throw new ServiceException("This account has been suspended.");
        }

        user.clearFailedLogins();
        user.setLastLoginAt(LocalDateTime.now());
        userAccountRepository.save(user);
        return user;
    }

    // not @Transactional (see note above) - each save below commits on its own,
    // independently of the ServiceException the caller throws right after this returns
    private void recordFailedAttempt(String email, String ipAddress, UserAccount user) {
        failedLoginAttemptRepository.save(new FailedLoginAttempt(email, ipAddress));
        if (user != null) {
            user.recordFailedLogin();
            userAccountRepository.save(user);
        }
    }

    private String rejectedMessage(UserAccount user) {
        String message = "Your registration was not approved.";
        if (user instanceof InteriorDesigner designer && designer.getRejectionReason() != null) {
            message += " Reason: " + designer.getRejectionReason();
        }
        return message;
    }
}
