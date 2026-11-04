package com.csit314.platform.dto;

public record CompanyProfileForm(String companyName,
                                 String companyDescription,
                                 String companyPhone,
                                 String serviceArea,
                                 String licenseNumber) {
}
