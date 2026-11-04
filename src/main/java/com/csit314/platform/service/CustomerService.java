package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.UserAccountRepository;
import com.csit314.platform.dto.CustomerRegistrationForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    public Customer register(CustomerRegistrationForm form) {
        return register(form, null);
    }

    // ADM-14 - ipAddress is optional (bulk/test data has none) and used to spot registration bursts
    @Transactional
    public Customer register(CustomerRegistrationForm form, String ipAddress) {
        InputRules.checkNewPassword(form.password(), form.confirmPassword());
        Customer customer = buildCustomer(form, passwordEncoder.encode(form.password()));
        customer.setRegistrationIp(ipAddress);
        return customerRepository.save(customer);
    }

    // Anthony's review on PR #25 - DemoDataImportService registers potentially
    // thousands of synthetic accounts that all share one password on purpose; calling
    // the normal register() still re-ran BCrypt (~100ms) once per account for a
    // password that hashes to the same thing every time. This lets the caller hash the
    // shared password once and pass the hash straight through. Package-private so nothing
    // outside this package can save an account with an unverified "pre-encoded" string
    @Transactional
    Customer registerWithEncodedPassword(CustomerRegistrationForm form, String encodedPassword) {
        return customerRepository.save(buildCustomer(form, encodedPassword));
    }

    private Customer buildCustomer(CustomerRegistrationForm form, String encodedPassword) {
        String name = InputRules.requireText(form.name(), "Full name", 100);
        String email = InputRules.requireEmail(form.email());

        if (userAccountRepository.existsByEmail(email)) {
            throw new ServiceException("An account with this email already exists.");
        }

        return new Customer(name, email, encodedPassword);
    }

    // the logged-in customer resolved from their own session - should always exist
    @Transactional(readOnly = true)
    public Customer requireById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ServiceException("Customer account not found."));
    }

    // looking up some other customer by id for display - may not exist, caller decides what to do
    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {
        return customerRepository.findById(id);
    }
}
