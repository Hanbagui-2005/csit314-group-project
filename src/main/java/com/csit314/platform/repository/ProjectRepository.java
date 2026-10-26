package com.csit314.platform.repository;

import com.csit314.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // IDR-10 - Browse search, filters on keyword and/or category when given
    @Query("select p from Project p where "
            + "(:query is null or lower(p.title) like lower(concat('%', :query, '%')) "
            + "or lower(p.description) like lower(concat('%', :query, '%'))) "
            + "and (:categoryName is null or p.category.name = :categoryName) "
            + "order by p.createdAt desc")
    List<Project> search(@Param("query") String query, @Param("categoryName") String categoryName);
}
