package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.web.dto.CustomerRegistrationForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerService(CustomerRepository customerRepository,
                           UserAccountRepository userAccountRepository,
                           PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // CUS-01: validate the form and save a new active customer
    @Transactional
    public Customer register(CustomerRegistrationForm form) {
        String name = InputRules.requireText(form.name(), "Full name", 100);
        String email = InputRules.requireEmail(form.email());
        InputRules.checkNewPassword(form.password(), form.confirmPassword());

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException("An account with this email already exists.");
        }

        Customer customer = new Customer(name, email, passwordEncoder.encode(form.password()));
        return customerRepository.save(customer);
    }
}
