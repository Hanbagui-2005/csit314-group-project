package com.csit314.platform.dto;

// fields posted by designer-profile-edit.html; licenseNumber isn't here on purpose - that
// stays admin-controlled since it drives the verified badge (CUS-09)
public record CompanyProfileForm(String companyName,
                                 String companyDescription,
                                 String companyPhone,
                                 String serviceArea) {
}
