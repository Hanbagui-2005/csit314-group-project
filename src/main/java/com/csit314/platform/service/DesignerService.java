package com.csit314.platform.service;

import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.dto.DesignerRegistrationForm;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.LicenseVerification;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.LicenseVerificationRepository;
import com.csit314.platform.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DesignerService {

    private static final Logger log =
            LoggerFactory.getLogger(DesignerService.class);

    private final InteriorDesignerRepository designerRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final LicenseVerificationRepository verificationRepository;
    private final EntityManager entityManager;

    public DesignerService(
            InteriorDesignerRepository designerRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            LicenseVerificationRepository verificationRepository,
            EntityManager entityManager) {
        this.designerRepository = designerRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationRepository = verificationRepository;
        this.entityManager = entityManager;
    }

    public InteriorDesigner register(DesignerRegistrationForm form) {
        return register(form, null);
    }

    @Transactional
    public InteriorDesigner register(
            DesignerRegistrationForm form, String ipAddress) {
        String contactName = InputRules.requireText(
                form.contactName(), "Contact name", 100);
        String email = InputRules.requireEmail(form.email());
        InputRules.checkNewPassword(form.password(), form.confirmPassword());

        String companyName = InputRules.requireText(
                form.companyName(), "Company name", 150);
        String description = InputRules.optionalText(
                form.companyDescription(), 5000, "Company description");
        String phone = InputRules.optionalText(
                form.companyPhone(), 30, "Contact phone");
        String licenseNumber = InputRules.optionalText(
                form.licenseNumber(), 60, "License number");
        String serviceArea = InputRules.optionalText(
                form.serviceArea(), 200, "Service area");

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException(
                    "An account with this email already exists.");
        }

        Company company = new Company(companyName, description, phone);
        company.setLicenseNumber(licenseNumber);
        company.setServiceArea(serviceArea);

        InteriorDesigner designer = new InteriorDesigner(
                contactName,
                email,
                passwordEncoder.encode(form.password()),
                company);
        designer.setRegistrationIp(ipAddress);

        InteriorDesigner saved = designerRepository.save(designer);

        log.info("New designer registration pending approval: {} ({})",
                companyName, email);

        return saved;
    }

    @Transactional(readOnly = true)
    public InteriorDesigner requireById(Long id) {
        return designerRepository.findById(id)
                .orElseThrow(() ->
                        new ServiceException("Designer account not found."));
    }

    @Transactional(readOnly = true)
    public Optional<InteriorDesigner> findById(Long id) {
        return designerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<InteriorDesigner> listAll() {
        return designerRepository.findAll();
    }

    @Transactional
    public void setServiceAreaVerified(Long designerId, boolean verified) {
        InteriorDesigner designer = requireById(designerId);
        designer.setServiceAreaVerified(verified);
    }

    @Transactional
    public InteriorDesigner updateCompanyProfile(
            Long designerId, CompanyProfileForm form) {
        String name = InputRules.requireText(
                form.companyName(), "Company name", 150);
        String description = InputRules.optionalText(
                form.companyDescription(), 5000, "Company description");
        String phone = InputRules.optionalText(
                form.companyPhone(), 30, "Contact phone");
        String serviceArea = InputRules.optionalText(
                form.serviceArea(), 200, "Service area");
        String licenseNumber = InputRules.optionalText(
                form.licenseNumber(), 60, "License number");

        InteriorDesigner designer = requireLockedDesigner(designerId);
        Company company = designer.getCompany();

        String previousLicenseNumber = InputRules.optionalText(
                company.getLicenseNumber(), 60, "License number");

        if (!Objects.equals(previousLicenseNumber, licenseNumber)) {
            String note = "License number changed from ["
                    + displayLicense(previousLicenseNumber)
                    + "] to ["
                    + displayLicense(licenseNumber)
                    + "]. A fresh admin verification is required.";

            verificationRepository.save(new LicenseVerification(
                    designer,
                    licenseNumber,
                    LicenseVerification.Action.LICENSE_CHANGED,
                    designer.getEmail(),
                    note));

            designer.setVerified(false);
        }

        company.setLicenseNumber(licenseNumber);
        company.setName(name);
        company.setDescription(description);
        company.setPhone(phone);

        if (!Objects.equals(company.getServiceArea(), serviceArea)) {
            company.setServiceArea(serviceArea);
            designer.setServiceAreaVerified(false);
        }

        return designer;
    }

    private InteriorDesigner requireLockedDesigner(Long designerId) {
        InteriorDesigner designer = entityManager.find(
                InteriorDesigner.class,
                designerId,
                LockModeType.PESSIMISTIC_WRITE);

        if (designer == null) {
            throw new ServiceException("Designer account not found.");
        }

        entityManager.refresh(designer);
        return designer;
    }

    private String displayLicense(String licenseNumber) {
        return licenseNumber == null ? "not provided" : licenseNumber;
    }
}
