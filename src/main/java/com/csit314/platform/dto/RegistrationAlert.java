package com.csit314.platform.dto;

import java.util.List;

// ADM-14 - more than a few sign-ups from the same IP in a short window
public record RegistrationAlert(String ipAddress, int count, List<String> emails) {
}
