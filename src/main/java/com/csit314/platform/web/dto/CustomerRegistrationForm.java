package com.csit314.platform.web.dto;

// fields posted by register.html (CUS-01)
public record CustomerRegistrationForm(String name, String email, String password, String confirmPassword) {
}
