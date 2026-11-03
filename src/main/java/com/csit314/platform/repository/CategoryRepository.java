package com.csit314.platform.repository;

import com.csit314.platform.model.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    // supports PM-01 - renaming to the same name as another category isn't a duplicate against itself
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // PM-13 - only active categories are offered for management or for new projects;
    // a deactivated one stays in the database (so existing projects keep their category)
    // but drops out of every list a customer or designer would pick from
    List<Category> findByActiveTrue(Sort sort);
}
