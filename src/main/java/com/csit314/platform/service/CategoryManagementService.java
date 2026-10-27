package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Platform Management actions on IDP categories
@Service
public class CategoryManagementService {

    private final CategoryRepository categoryRepository;
    private final ProjectRepository projectRepository;

    public CategoryManagementService(CategoryRepository categoryRepository, ProjectRepository projectRepository) {
        this.categoryRepository = categoryRepository;
        this.projectRepository = projectRepository;
    }

    // PM-02
    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // PM-02
    @Transactional
    public Category addCategory(String name) {
        String trimmed = InputRules.requireText(name, "Category name", 60);
        if (categoryRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ServiceException("A category with that name already exists.");
        }
        return categoryRepository.save(new Category(trimmed));
    }

    // PM-03
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

    // PM-04 - a category still used by at least one project can't be deleted, so projects
    // never end up pointing at a category that no longer exists
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        if (projectRepository.countByCategoryId(categoryId) > 0) {
            throw new ServiceException("This category is still used by at least one project and can't be deleted.");
        }
        categoryRepository.delete(category);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ServiceException("Category not found."));
    }
}
