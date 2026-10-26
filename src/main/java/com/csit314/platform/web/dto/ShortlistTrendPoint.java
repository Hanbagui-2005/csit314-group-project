package com.csit314.platform.web.dto;

import java.time.LocalDate;

// IDR-12 - how many shortlists a designer's portfolio picked up on a given day
public record ShortlistTrendPoint(LocalDate date, long count) {
}
