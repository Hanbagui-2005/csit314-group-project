package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// IDR-06 - looks for category keywords in the title/description so the designer
// doesn't have to pick the category manually every time
@Service
public class CategorySuggestionService {

    // keep each keyword from being a substring of another one in the same list,
    // otherwise one word in the text (e.g. "minimalist") scores double and can
    // out-vote a single real match in a different category (e.g. "kitchen")
    private static final Map<String, List<String>> KEYWORDS_BY_CATEGORY = Map.of(
            "Kitchen", List.of("kitchen", "cook", "stove", "oven", "cabinet", "countertop", "pantry"),
            "Living Room", List.of("living", "lounge", "sofa", "couch", "tv unit", "entertainment"),
            "Bedroom", List.of("bed", "sleep", "closet", "wardrobe", "nursery"),
            "Bathroom", List.of("bath", "shower", "toilet", "vanity", "sink"),
            "Minimal", List.of("minimal", "simple", "scandinavian", "decluttered"),
            "Budget", List.of("budget", "affordable", "cheap", "low-cost", "low cost", "economical")
    );

    private final CategoryRepository categoryRepository;

    public CategorySuggestionService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Optional<Category> suggest(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return Optional.empty();
        }

        String bestCategoryName = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> entry : KEYWORDS_BY_CATEGORY.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategoryName = entry.getKey();
            }
        }

        if (bestCategoryName == null) {
            return Optional.empty();
        }
        String matchedName = bestCategoryName;
        return categoryRepository.findAll().stream()
                .filter(cat -> cat.getName().equalsIgnoreCase(matchedName))
                .findFirst();
    }
}
