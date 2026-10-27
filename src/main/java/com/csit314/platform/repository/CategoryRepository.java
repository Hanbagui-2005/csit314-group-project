package com.csit314.platform.repository;

import com.csit314.platform.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    // PM-03 - renaming to the same name as another category isn't a duplicate against itself
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
