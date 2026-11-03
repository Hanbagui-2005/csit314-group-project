package com.csit314.platform.repository;

import com.csit314.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // supports PM-01 - a category still used by at least one project can't be deleted
    long countByCategoryId(Long categoryId);

    // PM-06/PM-08 - activity reports
    long countByCreatedAtAfter(LocalDateTime since);

    // PM-07 - active designers who have at least one project in this category
    @Query("select count(distinct p.designer.id) from Project p "
            + "where p.category.id = :categoryId and p.designer.status = com.csit314.platform.model.AccountStatus.ACTIVE")
    long countDistinctActiveDesignersByCategoryId(@Param("categoryId") Long categoryId);

    // IDR-10/CUS-04/CUS-09/CUS-10/PM-05 - Browse search, filters on keyword/category/budget/room
    // size/verified/location when given. Only shows projects from ACTIVE designers - a suspended,
    // pending or rejected designer's work shouldn't be browsable. The location filter is what
    // actually puts PM-05's service-area check in front of customers, instead of it only being
    // visible on the Platform Management page.
    // :query and :location are escaped by the caller (BrowseService) before being passed
    // in here, so a % or _ typed into a search box is matched literally instead of acting
    // as a wildcard
    @Query("select p from Project p where "
            + "(:query is null or lower(p.title) like lower(concat('%', :query, '%')) escape '\\' "
            + "or lower(p.description) like lower(concat('%', :query, '%')) escape '\\') "
            + "and (:categoryName is null or p.category.name = :categoryName) "
            + "and (:maxBudget is null or p.budgetMax <= :maxBudget) "
            + "and (:minRoomSize is null or p.roomSizeSqm <= :minRoomSize) "
            + "and (:verifiedOnly = false or p.designer.verified = true) "
            + "and (:location is null or lower(p.designer.company.serviceArea) like lower(concat('%', :location, '%')) escape '\\') "
            + "and p.designer.status = com.csit314.platform.model.AccountStatus.ACTIVE "
            + "order by p.createdAt desc")
    List<Project> search(@Param("query") String query, @Param("categoryName") String categoryName,
                         @Param("maxBudget") Integer maxBudget, @Param("minRoomSize") Integer minRoomSize,
                         @Param("verifiedOnly") boolean verifiedOnly, @Param("location") String location);

    // CUS-12 - only listings that actually gave a budget, to estimate a range from
    List<Project> findByCategoryNameAndBudgetMinIsNotNull(String categoryName);

    // CUS-05 - other projects in categories the customer already likes
    List<Project> findByCategoryIdInAndIdNotInOrderByCreatedAtDesc(List<Long> categoryIds, List<Long> excludeIds);

    // PM-10 - total views across all of a category's projects, so PM can see what's popular
    @Query("select coalesce(sum(p.viewCount), 0) from Project p where p.category.id = :categoryId")
    long sumViewCountByCategoryId(@Param("categoryId") Long categoryId);
}
