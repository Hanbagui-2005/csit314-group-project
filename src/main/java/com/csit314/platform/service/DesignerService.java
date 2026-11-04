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

    private static final Logger log = LoggerFactory.getLogger(DesignerService.class);

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

    // IDR-01: save the designer and their company; the account stays PENDING until an admin reviews it
    public InteriorDesigner register(DesignerRegistrationForm form) {
        return register(form, null);
    }

    // ADM-14 - ipAddress is optional (bulk/test data has none) and used to spot registration bursts
    @Transactional
    public InteriorDesigner register(DesignerRegistrationForm form, String ipAddress) {
        String contactName = InputRules.requireText(form.contactName(), "Contact name", 100);
        String email = InputRules.requireEmail(form.email());
        InputRules.checkNewPassword(form.password(), form.confirmPassword());
        String companyName = InputRules.requireText(form.companyName(), "Company name", 150);
        String description = InputRules.optionalText(form.companyDescription(), 5000, "Company description");
        String phone = InputRules.optionalText(form.companyPhone(), 30, "Contact phone");
        String licenseNumber = InputRules.optionalText(form.licenseNumber(), 60, "License number");
        String serviceArea = InputRules.optionalText(form.serviceArea(), 200, "Service area");

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException("An account with this email already exists.");
        }

        Company company = new Company(companyName, description, phone);
        company.setLicenseNumber(licenseNumber);
        company.setServiceArea(serviceArea);
        InteriorDesigner designer = new InteriorDesigner(contactName, email,
                passwordEncoder.encode(form.password()), company);
        designer.setRegistrationIp(ipAddress);
        InteriorDesigner saved = designerRepository.save(designer);

        // ADM-12 - no mail server yet (same as password reset / weekly digest), so this
        // goes to the log; the admin users page also shows a live pending-count badge
        log.info("New designer registration pending approval: {} ({})", companyName, email);
        return saved;
    }

    // the logged-in designer resolved from their own session - should always exist
    @Transactional(readOnly = true)
    public InteriorDesigner requireById(Long id) {
        return designerRepository.findById(id)
                .orElseThrow(() -> new ServiceException("Designer account not found."));
    }

    // looking up some other designer by id for display - may not exist, caller decides what to do
    @Transactional(readOnly = true)
    public Optional<InteriorDesigner> findById(Long id) {
        return designerRepository.findById(id);
    }

    // PM-05 - every designer, so Platform Management can review declared service areas
    @Transactional(readOnly = true)
    public List<InteriorDesigner> listAll() {
        return designerRepository.findAll();
    }

    // PM-05
    @Transactional
    public void setServiceAreaVerified(Long designerId, boolean verified) {
        InteriorDesigner designer = requireById(designerId);
        designer.setServiceAreaVerified(verified);
    }

    // lets a designer keep their own company details current after registration -
    // the license number isn't editable here since that drives the verified badge
    // (CUS-09) and stays admin-controlled
    @Transactional
    public InteriorDesigner updateCompanyProfile(InteriorDesigner designer, CompanyProfileForm form) {
        String name = InputRules.requireText(form.companyName(), "Company name", 150);
        String description = InputRules.optionalText(form.companyDescription(), 5000, "Company description");
        String phone = InputRules.optionalText(form.companyPhone(), 30, "Contact phone");
        String serviceArea = InputRules.optionalText(form.serviceArea(), 200, "Service area");

        Company company = designer.getCompany();
        company.setName(name);
        company.setDescription(description);
        company.setPhone(phone);
        // PM-05 - a changed service area needs re-checking, so don't leave the old
        // verified flag on an area Platform Management never actually confirmed
        if (!Objects.equals(company.getServiceArea(), serviceArea)) {
            company.setServiceArea(serviceArea);
            designer.setServiceAreaVerified(false);
        }
        return designer;
    }
}
