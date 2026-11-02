package com.csit314.platform.dto;

// IDR-06 - json response for the category suggestion endpoint, both fields null means no suggestion
public record CategorySuggestionResponse(Long categoryId, String categoryName) {
}
