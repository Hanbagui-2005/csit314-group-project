package com.csit314.platform.service;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.LicenseVerification;
import com.csit314.platform.model.Role;
import com.csit314.platform.model.UserAccount;
import com.csit314.platform.repository.LicenseVerificationRepository;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class LicenseVerificationService {

    private final EntityManager entityManager;
    private final UserAccountRepository userAccountRepository;
    private final LicenseVerificationRepository verificationRepository;

    public LicenseVerificationService(
            EntityManager entityManager,
            UserAccountRepository userAccountRepository,
            LicenseVerificationRepository verificationRepository) {
        this.entityManager = entityManager;
        this.userAccountRepository = userAccountRepository;
        this.verificationRepository = verificationRepository;
    }

    @Transactional
    public InteriorDesigner verify(
            Long designerId,
            Long adminId,
            String expectedLicenseNumber,
            String note) {
        UserAccount admin = requireAdmin(adminId);
        String evidence = InputRules.requireText(
                note, "Verification evidence", 2000);

        InteriorDesigner designer = requireLockedDesigner(designerId);
        String licenseNumber = requireUnchangedLicense(
                designer, expectedLicenseNumber);

        if (licenseNumber == null) {
            throw new ServiceException(
                    "A license number is required before verification.");
        }

        if (designer.isVerified()) {
            throw new ServiceException(
                    "This designer is already verified.");
        }

        verificationRepository.save(new LicenseVerification(
                designer,
                licenseNumber,
                LicenseVerification.Action.VERIFIED,
                admin.getEmail(),
                evidence));

        designer.setVerified(true);
        return designer;
    }

    @Transactional
    public InteriorDesigner revoke(
            Long designerId,
            Long adminId,
            String expectedLicenseNumber,
            String note) {
        UserAccount admin = requireAdmin(adminId);
        String reason = InputRules.requireText(
                note, "Reason for revoking verification", 2000);

        InteriorDesigner designer = requireLockedDesigner(designerId);
        String licenseNumber = requireUnchangedLicense(
                designer, expectedLicenseNumber);

        if (!designer.isVerified()) {
            throw new ServiceException(
                    "This designer is not currently verified.");
        }

        verificationRepository.save(new LicenseVerification(
                designer,
                licenseNumber,
                LicenseVerification.Action.REVOKED,
                admin.getEmail(),
                reason));

        designer.setVerified(false);
        return designer;
    }

    @Transactional(readOnly = true)
    public List<LicenseVerification> historyFor(Long designerId) {
        return verificationRepository
                .findByDesignerIdOrderByRecordedAtDescIdDesc(designerId);
    }

    private UserAccount requireAdmin(Long adminId) {
        UserAccount admin = userAccountRepository.findById(adminId)
                .orElseThrow(() ->
                        new ServiceException("Admin account not found."));

        if (admin.getRole() != Role.ADMIN
                || admin.getStatus() != AccountStatus.ACTIVE) {
            throw new ServiceException(
                    "An active admin account is required.");
        }

        return admin;
    }

    private InteriorDesigner requireLockedDesigner(Long designerId) {
        InteriorDesigner designer = entityManager.find(
                InteriorDesigner.class,
                designerId,
                LockModeType.PESSIMISTIC_WRITE);

        if (designer == null) {
            throw new ServiceException("Interior Designer not found.");
        }

        entityManager.refresh(designer);
        return designer;
    }

    private String requireUnchangedLicense(
            InteriorDesigner designer,
            String expectedLicenseNumber) {
        String current = InputRules.optionalText(
                designer.getCompany().getLicenseNumber(),
                60,
                "License number");

        String expected = InputRules.optionalText(
                expectedLicenseNumber,
                60,
                "License number");

        if (!Objects.equals(current, expected)) {
            throw new ServiceException(
                    "The license number has changed. "
                            + "Refresh the page and review it again.");
        }

        return current;
    }
}
