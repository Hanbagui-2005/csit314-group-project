package com.csit314.platform.service;

import java.util.Locale;
import java.util.regex.Pattern;

// shared validation for registration and password forms
final class InputRules {

    static final int MIN_PASSWORD_LENGTH = 8;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private InputRules() {
    }

    // emails are stored lower-case so logins aren't case sensitive
    static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String requireEmail(String email) {
        String normalised = normaliseEmail(email);
        if (!EMAIL.matcher(normalised).matches()) {
            throw new ServiceException("Please enter a valid email address.");
        }
        return normalised;
    }

    static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(fieldName + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ServiceException(fieldName + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    static String optionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ServiceException(fieldName + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    // CUS-16 - length alone isn't "strength", so this also needs a letter and a digit
    static void checkNewPassword(String password, String confirmPassword) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!containsLetter(password) || !containsDigit(password)) {
            throw new ServiceException("Password must include at least one letter and one number.");
        }
        if (!password.equals(confirmPassword)) {
            throw new ServiceException("Passwords do not match.");
        }
    }

    private static boolean containsLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private static boolean containsDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }
}
