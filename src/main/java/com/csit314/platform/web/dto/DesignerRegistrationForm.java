package com.csit314.platform.web.dto;

// fields posted by designer-register.html (IDR-01)
public record DesignerRegistrationForm(String contactName,
                                       String email,
                                       String password,
                                       String confirmPassword,
                                       String companyName,
                                       String companyDescription,
                                       String companyPhone) {
}
