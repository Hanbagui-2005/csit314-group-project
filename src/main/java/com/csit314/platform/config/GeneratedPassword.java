package com.csit314.platform.config;

import java.security.SecureRandom;

// a fixed, publicly-documented default password is a real risk if someone deploys without
// overriding it - generate a random one instead whenever the env var isn't set
final class GeneratedPassword {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private GeneratedPassword() {
    }

    static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return password.toString();
    }
}
