package com.csit314.platform.repository;

import com.csit314.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // IDR-10/CUS-04 - Browse search, filters on keyword/category/budget/room size when given
    @Query("select p from Project p where "
            + "(:query is null or lower(p.title) like lower(concat('%', :query, '%')) "
            + "or lower(p.description) like lower(concat('%', :query, '%'))) "
            + "and (:categoryName is null or p.category.name = :categoryName) "
            + "and (:maxBudget is null or p.budgetMax <= :maxBudget) "
            + "and (:minRoomSize is null or p.roomSizeSqm >= :minRoomSize) "
            + "order by p.createdAt desc")
    List<Project> search(@Param("query") String query, @Param("categoryName") String categoryName,
                         @Param("maxBudget") Integer maxBudget, @Param("minRoomSize") Integer minRoomSize);

    // CUS-12 - only listings that actually gave a budget, to estimate a range from
    List<Project> findByCategoryNameAndBudgetMinIsNotNull(String categoryName);
}
