package com.csit314.platform.dto;

import com.csit314.platform.model.Category;

// Anthony's review (PR #21) - the controller used to guess whether addCategory()
// merged into a near-duplicate by comparing the returned category's spelling against
// what was typed, which gives the wrong answer for a case-insensitive exact match that
// got reactivated (e.g. typed "kitchen", reactivated row is "Kitchen" - spelling
// differs, but nothing was merged). The service says explicitly instead
public record CategoryAddResult(Category category, boolean mergedIntoNearDuplicate) {

    public static CategoryAddResult addedOrReactivated(Category category) {
        return new CategoryAddResult(category, false);
    }

    public static CategoryAddResult mergedIntoNearDuplicate(Category category) {
        return new CategoryAddResult(category, true);
    }
}
