package com.csit314.platform.web.dto;

// IDR-13 - fields posted from the contact-designer form; projectId is optional
public record ContactDesignerForm(String body, Long projectId) {
}
