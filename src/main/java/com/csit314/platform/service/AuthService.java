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

    // not its own numbered Taiga story, same as the Platform Manager account seeding
    public UserAccount loginPlatformManager(String email, String password, String ipAddress) {
        return login(email, password, Role.PLATFORM_MANAGER, ipAddress);
    }

    private UserAccount login(String email, String password, Role expectedRole, String ipAddress) {
        String normalisedEmail = InputRules.normaliseEmail(email);
        Optional<UserAccount> maybeUser = userAccountRepository.findByEmail(normalisedEmail);
        UserAccount user = maybeUser.orElse(null);

        // ADM-09/13 - a locked account rejects every attempt with the exact same message,
        // whatever password is given. Checking the password first (and only showing the
        // lock once it's right) would let an attacker tell, from the response alone, the
        // moment they'd guessed correctly - which defeats the point of locking the account
        // in the first place. Showing a distinct "locked" message before checking the
        // password at all would instead reveal that the email has an account, locked or
        // not. Returning the identical INVALID_CREDENTIALS message either way avoids both
        if (user != null && user.getRole() == expectedRole && user.isLocked()) {
            recordFailedAttempt(normalisedEmail, ipAddress, user);
            throw new ServiceException(INVALID_CREDENTIALS);
        }

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

        LocalDateTime loginAt = LocalDateTime.now();
        user.clearFailedLogins();
        user.setLastLoginAt(loginAt);
        userAccountRepository.recordSuccessfulLogin(user.getId(), loginAt);
        return user;
    }

    // not @Transactional (see note above) - each save below commits on its own,
    // independently of the ServiceException the caller throws right after this returns
    private void recordFailedAttempt(String email, String ipAddress, UserAccount user) {
        failedLoginAttemptRepository.save(new FailedLoginAttempt(email, ipAddress));
        if (user != null) {
            // the in-memory copy only keeps this object consistent for the caller; the
            // stored count comes from the two atomic updates below, never from this copy
            user.recordFailedLogin();
            LocalDateTime now = LocalDateTime.now();
            userAccountRepository.countFailedLogin(
                    user.getId(), now.minusMinutes(UserAccount.LOCK_DURATION_MINUTES));
            userAccountRepository.lockIfOverLimit(
                    user.getId(), UserAccount.MAX_FAILED_ATTEMPTS, now);
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
