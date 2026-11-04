package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// IDR-06 - suggests a category from title/description keywords, so the designer
// doesn't have to pick one manually every time
@ExtendWith(MockitoExtension.class)
class CategorySuggestionServiceUnitTests {

    @Mock CategoryRepository categoryRepository;

    private CategorySuggestionService service() {
        return new CategorySuggestionService(categoryRepository);
    }

    @Test
    void suggestsTheMatchingCategoryWhenItsKeywordAppearsInTheTitle() {
        Category kitchen = new Category("Kitchen");
        when(categoryRepository.findByActiveTrue(Sort.unsorted())).thenReturn(List.of(kitchen));

        var result = service().suggest("New kitchen remodel", "");

        assertThat(result).contains(kitchen);
    }

    @Test
    void suggestsTheMatchingCategoryWhenItsKeywordAppearsInTheDescription() {
        Category bathroom = new Category("Bathroom");
        when(categoryRepository.findByActiveTrue(Sort.unsorted())).thenReturn(List.of(bathroom));

        var result = service().suggest("Project 12", "A full shower and vanity upgrade");

        assertThat(result).contains(bathroom);
    }

    @Test
    void suggestsNothingWhenNoKeywordsMatchAnything() {
        var result = service().suggest("A lovely renovation", "Nothing specific here");

        assertThat(result).isEmpty();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void suggestsNothingForBlankTitleAndDescription() {
        var result = service().suggest("  ", "  ");

        assertThat(result).isEmpty();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void treatsNullTitleAndDescriptionAsBlank() {
        var result = service().suggest(null, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(categoryRepository);
    }

    // the category with the most matching keywords wins, not just whichever
    // category happened to be checked first
    @Test
    void theCategoryWithTheHighestKeywordScoreWins() {
        Category kitchen = new Category("Kitchen");
        when(categoryRepository.findByActiveTrue(Sort.unsorted())).thenReturn(List.of(kitchen));

        // 3 kitchen keywords (kitchen, stove, oven) outscore 1 Minimal keyword (simple)
        var result = service().suggest("A simple kitchen with a stove and oven", "");

        assertThat(result).contains(kitchen);
    }

    @Test
    void returnsEmptyWhenTheMatchedCategoryIsntActive() {
        when(categoryRepository.findByActiveTrue(Sort.unsorted())).thenReturn(List.of());

        var result = service().suggest("New kitchen remodel", "");

        assertThat(result).isEmpty();
    }

    @Test
    void matchingAgainstTheActiveCategoryListIsCaseInsensitive() {
        Category kitchen = new Category("KITCHEN");
        when(categoryRepository.findByActiveTrue(Sort.unsorted())).thenReturn(List.of(kitchen));

        var result = service().suggest("New kitchen remodel", "");

        assertThat(result).contains(kitchen);
    }
}
