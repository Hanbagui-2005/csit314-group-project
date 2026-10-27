package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.RegistrationAlert;
import com.csit314.platform.web.dto.UserSummary;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// User Admin actions on registered accounts
@Service
public class UserManagementService {

    // ADM-14 - this many sign-ups from the same IP within the window counts as a burst
    private static final int BURST_THRESHOLD = 3;
    private static final int BURST_WINDOW_HOURS = 1;

    private final UserAccountRepository userAccountRepository;
    private final InteriorDesignerRepository designerRepository;

    public UserManagementService(UserAccountRepository userAccountRepository,
                                 InteriorDesignerRepository designerRepository) {
        this.userAccountRepository = userAccountRepository;
        this.designerRepository = designerRepository;
    }

    // ADM-02: every registered account, newest first
    @Transactional(readOnly = true)
    public List<UserSummary> listAllUsers() {
        return userAccountRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toSummary)
                .toList();
    }

    private UserSummary toSummary(UserAccount user) {
        String licenseNumber = null;
        Boolean verified = null;
        Integer riskScore = null;
        if (user instanceof InteriorDesigner designer) {
            licenseNumber = designer.getCompany().getLicenseNumber();
            verified = designer.isVerified();
            riskScore = riskScore(designer);
        }
        return new UserSummary(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole().getLabel(), user.getStatus().getLabel(), user.isLocked(),
                licenseNumber, verified, riskScore);
    }

    // ADM-07 - 0 (complete profile) to 100 (nothing filled in); only meaningful while PENDING,
    // but harmless to compute for any designer
    private int riskScore(InteriorDesigner designer) {
        int risk = 100;
        if (designer.getCompany().getDescription() != null && !designer.getCompany().getDescription().isBlank()) {
            risk -= 25;
        }
        if (designer.getCompany().getPhone() != null && !designer.getCompany().getPhone().isBlank()) {
            risk -= 25;
        }
        if (designer.getCompany().getLicenseNumber() != null && !designer.getCompany().getLicenseNumber().isBlank()) {
            risk -= 25;
        }
        if (designer.getContactName() != null && designer.getContactName().trim().contains(" ")) {
            risk -= 25;
        }
        return risk;
    }

    // ADM-03
    @Transactional
    public InteriorDesigner approveDesigner(Long designerId) {
        InteriorDesigner designer = findPendingDesigner(designerId);
        designer.approve();
        return designer;
    }

    // ADM-04
    @Transactional
    public InteriorDesigner rejectDesigner(Long designerId, String reason) {
        String trimmedReason = InputRules.requireText(reason, "A reason for rejecting", 1000);
        InteriorDesigner designer = findPendingDesigner(designerId);
        designer.reject(trimmedReason);
        return designer;
    }

    // ADM-08 - best-effort: keeps going and reports how many actually succeeded
    @Transactional
    public int bulkApproveDesigners(List<Long> designerIds) {
        int succeeded = 0;
        for (Long id : designerIds) {
            try {
                approveDesigner(id);
                succeeded++;
            } catch (ServiceException ignored) {
                // skip ones that are no longer pending and keep going
            }
        }
        return succeeded;
    }

    // ADM-06 - admin accounts are excluded: suspending the only admin would lock everyone
    // out of /admin/** with no way back in
    @Transactional
    public UserAccount suspend(Long userId) {
        UserAccount user = findAccount(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ServiceException("Admin accounts can't be suspended.");
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ServiceException("Only an active account can be suspended.");
        }
        user.setStatus(AccountStatus.SUSPENDED);
        return user;
    }

    // ADM-06
    @Transactional
    public UserAccount reactivate(Long userId) {
        UserAccount user = findAccount(userId);
        if (user.getStatus() != AccountStatus.SUSPENDED) {
            throw new ServiceException("Only a suspended account can be reactivated.");
        }
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    // ADM-08
    @Transactional
    public int bulkSuspend(List<Long> userIds) {
        int succeeded = 0;
        for (Long id : userIds) {
            try {
                suspend(id);
                succeeded++;
            } catch (ServiceException ignored) {
                // skip ones that aren't active and keep going
            }
        }
        return succeeded;
    }

    // ADM-09 - clears both the lock and the failed-attempt counter behind it
    @Transactional
    public UserAccount unlock(Long userId) {
        UserAccount user = findAccount(userId);
        if (!user.isLocked()) {
            throw new ServiceException("This account isn't locked.");
        }
        user.clearFailedLogins();
        return user;
    }

    // ADM-05 - independent of approval; a designer could add a license later, or
    // admin could find a reason to revoke a verification
    @Transactional
    public InteriorDesigner toggleVerified(Long designerId) {
        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("Interior Designer not found."));
        designer.setVerified(!designer.isVerified());
        return designer;
    }

    // ADM-12
    @Transactional(readOnly = true)
    public long pendingDesignerCount() {
        return userAccountRepository.countByRoleAndStatus(Role.INTERIOR_DESIGNER, AccountStatus.PENDING);
    }

    // ADM-14 - groups every account created in the last BURST_WINDOW_HOURS by IP,
    // flags any IP with BURST_THRESHOLD+ sign-ups
    @Transactional(readOnly = true)
    public List<RegistrationAlert> registrationAlerts() {
        LocalDateTime since = LocalDateTime.now().minusHours(BURST_WINDOW_HOURS);
        List<UserAccount> recent = userAccountRepository.findAll().stream()
                .filter(user -> user.getRegistrationIp() != null && user.getCreatedAt() != null
                        && user.getCreatedAt().isAfter(since))
                .toList();

        Map<String, List<UserAccount>> byIp = new LinkedHashMap<>();
        for (UserAccount user : recent) {
            byIp.computeIfAbsent(user.getRegistrationIp(), ip -> new ArrayList<>()).add(user);
        }

        List<RegistrationAlert> alerts = new ArrayList<>();
        byIp.forEach((ip, accounts) -> {
            if (accounts.size() >= BURST_THRESHOLD) {
                List<String> emails = accounts.stream().map(UserAccount::getEmail).toList();
                alerts.add(new RegistrationAlert(ip, accounts.size(), emails));
            }
        });
        return alerts;
    }

    private UserAccount findAccount(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ServiceException("Account not found."));
    }

    private InteriorDesigner findPendingDesigner(Long designerId) {
        InteriorDesigner designer = designerRepository.findById(designerId)
                .orElseThrow(() -> new ServiceException("Interior Designer registration not found."));
        if (designer.getStatus() != AccountStatus.PENDING) {
            throw new ServiceException("This registration has already been reviewed.");
        }
        return designer;
    }
}
