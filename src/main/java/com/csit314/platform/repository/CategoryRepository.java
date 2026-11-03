package com.csit314.platform.repository;

import com.csit314.platform.model.Category;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // PM-13 - returns the category regardless of whether it's active, so the caller can
    // tell an exact-duplicate-of-an-active-one apart from a name that belongs to a
    // deactivated category (and so could be reactivated instead of rejected)
    Optional<Category> findByNameIgnoreCase(String name);

    // supports PM-01/PM-13 - renaming to the same name as another category isn't a
    // duplicate against itself; active or not, for the same reason as above
    Optional<Category> findByNameIgnoreCaseAndIdNot(String name, Long id);

    // PM-13 - only active categories are offered for management or for new projects;
    // a deactivated one stays in the database (so existing projects keep their category)
    // but drops out of every list a customer or designer would pick from
    List<Category> findByActiveTrue(Sort sort);
}
