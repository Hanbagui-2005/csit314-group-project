package com.csit314.platform.dto;

// covers IDR-10, still using fake numbers until real tracking is built
public record DesignerProjectStat(Long id, String title, String imageUrl, int views, int shortlists) {
}
