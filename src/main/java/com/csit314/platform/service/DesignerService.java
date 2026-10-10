package com.csit314.platform.service;

import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.DesignerRegistrationForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DesignerService {

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
    @Transactional
    public InteriorDesigner register(DesignerRegistrationForm form) {
        String contactName = InputRules.requireText(form.contactName(), "Contact name", 100);
        String email = InputRules.requireEmail(form.email());
        InputRules.checkNewPassword(form.password(), form.confirmPassword());
        String companyName = InputRules.requireText(form.companyName(), "Company name", 150);
        String description = InputRules.optionalText(form.companyDescription(), 5000, "Company description");
        String phone = InputRules.optionalText(form.companyPhone(), 30, "Contact phone");

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException("An account with this email already exists.");
        }

        Company company = new Company(companyName, description, phone);
        InteriorDesigner designer = new InteriorDesigner(contactName, email,
                passwordEncoder.encode(form.password()), company);
        return designerRepository.save(designer);
    }
}
