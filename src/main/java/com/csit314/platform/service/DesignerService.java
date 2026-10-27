package com.csit314.platform.service;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.DesignerRegistrationForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException("An account with this email already exists.");
        }

        Company company = new Company(companyName, description, phone);
        company.setLicenseNumber(licenseNumber);
        InteriorDesigner designer = new InteriorDesigner(contactName, email,
                passwordEncoder.encode(form.password()), company);
        designer.setRegistrationIp(ipAddress);
        InteriorDesigner saved = designerRepository.save(designer);

        // ADM-12 - no mail server yet (same as password reset / weekly digest), so this
        // goes to the log; the admin users page also shows a live pending-count badge
        log.info("New designer registration pending approval: {} ({})", companyName, email);
        return saved;
    }
}
