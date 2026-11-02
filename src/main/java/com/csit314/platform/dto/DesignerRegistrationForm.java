package com.csit314.platform.dto;

// fields posted by designer-register.html (IDR-01); licenseNumber and serviceArea are both
// optional (CUS-09, PM-05)
public record DesignerRegistrationForm(String contactName,
                                       String email,
                                       String password,
                                       String confirmPassword,
                                       String companyName,
                                       String companyDescription,
                                       String companyPhone,
                                       String licenseNumber,
                                       String serviceArea) {
}
