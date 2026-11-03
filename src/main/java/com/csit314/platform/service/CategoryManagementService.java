package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Platform Management actions on IDP categories
@Service
public class CategoryManagementService {

    private final CategoryRepository categoryRepository;
    private final ProjectRepository projectRepository;

    public CategoryManagementService(CategoryRepository categoryRepository, ProjectRepository projectRepository) {
        this.categoryRepository = categoryRepository;
        this.projectRepository = projectRepository;
    }

    // PM-01/PM-13 - deactivated categories are left out of management too
    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findByActiveTrue(Sort.by(Sort.Direction.ASC, "name"));
    }

    // PM-01/PM-03 - a near-duplicate of an existing category (e.g. "Kitchens" when
    // "Kitchen" already exists) reuses that category instead of fragmenting the
    // taxonomy with a second, near-identical one
    @Transactional
    public Category addCategory(String name) {
        String trimmed = InputRules.requireText(name, "Category name", 60);
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ServiceException("A category with that name already exists.");
        }
        Optional<Category> nearDuplicate = findNearDuplicate(trimmed, null);
        if (nearDuplicate.isPresent()) {
            return nearDuplicate.get();
        }
        return categoryRepository.save(new Category(trimmed));
    }

    // supports PM-01 - not its own tracked story (PM-03 is automatic duplicate-merging)
    @Transactional
    public Category renameCategory(Long categoryId, String newName) {
        Category category = findCategory(categoryId);
        String trimmed = InputRules.requireText(newName, "Category name", 60);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmed, categoryId)) {
            throw new ServiceException("A category with that name already exists.");
        }
        category.rename(trimmed);
        return category;
    }

    // PM-13 - soft delete, not a hard one: projects that already reference this category
    // keep referencing it (their category name still renders fine), but it drops out of
    // every list a customer or designer would pick a category from
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        category.deactivate();
    }

    // PM-03 - scans every active category for near-duplicates (normalisation
    // differences only - case, spacing, or a trailing plural) and merges each group
    // into one survivor, so categories that fragmented before this check existed (or
    // through some other path) get cleaned up too, not just newly-added ones
    @Transactional
    public void mergeDuplicateCategories() {
        List<Category> active = categoryRepository.findByActiveTrue(Sort.by(Sort.Direction.ASC, "name"));
        Map<String, List<Category>> byNormalisedName = active.stream()
                .collect(Collectors.groupingBy(c -> normalise(c.getName()), LinkedHashMap::new, Collectors.toList()));

        for (List<Category> group : byNormalisedName.values()) {
            if (group.size() < 2) {
                continue;
            }
            // alphabetically first survives - simple and deterministic, since there's
            // no meaningfully "more correct" spelling to prefer between near-duplicates
            Category survivor = group.get(0);
            for (Category duplicate : group.subList(1, group.size())) {
                for (Project project : projectRepository.findByCategoryId(duplicate.getId())) {
                    project.reassignCategory(survivor);
                }
                duplicate.deactivate();
            }
        }
    }

    private Optional<Category> findNearDuplicate(String candidateName, Long excludeId) {
        String normalisedCandidate = normalise(candidateName);
        return categoryRepository.findByActiveTrue(Sort.unsorted()).stream()
                .filter(c -> excludeId == null || !c.getId().equals(excludeId))
                .filter(c -> normalise(c.getName()).equals(normalisedCandidate))
                .findFirst();
    }

    // PM-03 - case/whitespace differences and a simple trailing plural ("Kitchen" vs
    // "Kitchens") count as the same category; this is deliberately simple rather than a
    // fuzzy similarity score, so it never merges two categories that just happen to
    // sound alike (e.g. "Kitchen" and "Kitchenette" stay separate)
    private String normalise(String name) {
        String cleaned = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return cleaned.length() > 1 && cleaned.endsWith("s") ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ServiceException("Category not found."));
    }
}
