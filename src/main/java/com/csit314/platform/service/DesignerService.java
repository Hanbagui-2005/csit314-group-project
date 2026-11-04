package com.csit314.platform.service;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.CompanyProfileForm;
import com.csit314.platform.dto.DesignerRegistrationForm;
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

    public DesignerService(InteriorDesignerRepository designerRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this.designerRepository = designerRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public InteriorDesigner register(DesignerRegistrationForm form) {
        return register(form, null);
    }

    @Transactional
    public InteriorDesigner register(DesignerRegistrationForm form,
                                     String ipAddress) {
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
        InteriorDesigner designer = requireById(designerId);

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

        Company company = designer.getCompany();

        company.setName(name);
        company.setDescription(description);
        company.setPhone(phone);

        if (!Objects.equals(company.getServiceArea(), serviceArea)) {
            company.setServiceArea(serviceArea);
            designer.setServiceAreaVerified(false);
        }

        // Changed license numbers require fresh admin verification.
        String currentLicenseNumber = InputRules.optionalText(
                company.getLicenseNumber(), 60, "License number");

        if (!Objects.equals(currentLicenseNumber, licenseNumber)) {
            designer.setVerified(false);
        }

        company.setLicenseNumber(licenseNumber);

        return designer;
    }
}
