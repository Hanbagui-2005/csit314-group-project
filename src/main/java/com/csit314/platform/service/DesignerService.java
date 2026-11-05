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
        InputRules.checkNewPassword(form.password(), form.confirmPassword());
        InteriorDesigner designer = buildDesigner(
                form, passwordEncoder.encode(form.password()));
        designer.setRegistrationIp(ipAddress);
        return saveAndLogPending(designer);
    }

    // Anthony's review on PR #25 - same reasoning as
    // CustomerService.registerWithEncodedPassword: DemoDataImportService shares one
    // password across every synthetic account, so hashing it once up front and
    // passing the hash through here skips re-running BCrypt per account. Package-private
    // for the same reason - only code in this package can bypass the normal per-registration hashing
    @Transactional
    InteriorDesigner registerWithEncodedPassword(
            DesignerRegistrationForm form, String encodedPassword) {
        return saveAndLogPending(buildDesigner(form, encodedPassword));
    }

    private InteriorDesigner buildDesigner(
            DesignerRegistrationForm form, String encodedPassword) {
        String contactName = InputRules.requireText(
                form.contactName(), "Contact name", 100);
        String email = InputRules.requireEmail(form.email());

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

        return new InteriorDesigner(contactName, email, encodedPassword, company);
    }

    private InteriorDesigner saveAndLogPending(InteriorDesigner designer) {
        InteriorDesigner saved = designerRepository.save(designer);
        log.info("New designer registration pending approval: {} ({})",
                designer.getCompany().getName(), designer.getEmail());
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
        String previousName = company.getName();

        boolean licenseChanged = !Objects.equals(previousLicenseNumber, licenseNumber);
        // Anthony's review on PR #27 - the verified badge really means "this license number,
        // under this company name, was checked by an admin". Renaming a verified company used
        // to leave the badge up even though the name an admin actually checked no longer
        // matches, so a rename alone now also clears it once there's something verified to
        // protect. Renaming an already-unverified company doesn't need a fresh verification
        // record - there's nothing to invalidate yet, and the license-number branch below
        // still covers it once a license number is actually entered
        boolean nameChanged = !Objects.equals(previousName, name) && designer.isVerified();

        company.setLicenseNumber(licenseNumber);
        company.setName(name);
        company.setDescription(description);
        company.setPhone(phone);

        // recorded after the company fields are updated, so the history entry's company
        // name (captured by LicenseVerification itself) is the new identity that now needs
        // a fresh admin check, matching the license number it records
        if (licenseChanged || nameChanged) {
            String note = identityChangeNote(
                    previousLicenseNumber, licenseNumber, licenseChanged,
                    previousName, name, nameChanged);

            verificationRepository.save(new LicenseVerification(
                    designer,
                    licenseNumber,
                    LicenseVerification.Action.LICENSE_CHANGED,
                    designer.getEmail(),
                    note));

            designer.setVerified(false);
        }

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

        @Transactional(readOnly = true)
    public String licenseReuseWarning(
            Long designerId, String licenseNumber) {
        String number = InputRules.optionalText(
                licenseNumber, 60, "License number");

        if (number == null) {
            return null;
        }

        List<String> companyNames = designerRepository
                .findByCompanyLicenseNumberIgnoreCaseAndIdNot(
                        number, designerId)
                .stream()
                .map(designer -> designer.getCompany().getName())
                .distinct()
                .sorted()
                .toList();

        if (companyNames.isEmpty()) {
            return null;
        }

        return "This license number is also used by: "
                + String.join(", ", companyNames)
                + ". Please check company ownership before verification. "
                + "Multiple designers may belong to the same company.";
    }

    private String displayLicense(String licenseNumber) {
        return licenseNumber == null ? "not provided" : licenseNumber;
    }

    private String identityChangeNote(String previousLicenseNumber, String licenseNumber,
                                      boolean licenseChanged, String previousName, String name,
                                      boolean nameChanged) {
        StringBuilder note = new StringBuilder();
        if (licenseChanged) {
            note.append("License number changed from [")
                    .append(displayLicense(previousLicenseNumber))
                    .append("] to [")
                    .append(displayLicense(licenseNumber))
                    .append("]. ");
        }
        if (nameChanged) {
            note.append("Company name changed from [")
                    .append(previousName)
                    .append("] to [")
                    .append(name)
                    .append("]. ");
        }
        note.append("A fresh admin verification is required.");
        return note.toString();
    }
}
