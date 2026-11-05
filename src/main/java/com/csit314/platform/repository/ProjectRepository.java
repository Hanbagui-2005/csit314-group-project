package com.csit314.platform.repository;

import com.csit314.platform.dto.TopDesigner;
import com.csit314.platform.dto.TopProject;
import com.csit314.platform.model.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // IDR-10 - incrementing Project.viewCount on the loaded entity made Hibernate write
    // back every column of that row, so a page view landing while the designer saved a
    // milestone or budget change could overwrite it with the values read before the edit
    // (and two simultaneous views could lose one count). This only touches view_count and
    // does the + 1 inside the database
    @Modifying(flushAutomatically = true)
    @Query("update Project p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int incrementViewCount(@Param("id") Long id);

    List<Project> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    // PM-03 - every project in a category being merged away, so they can be
    // reassigned to the surviving category
    List<Project> findByCategoryId(Long categoryId);

    // PM-02/PM-06/PM-08/PM-14 - new projects within an arbitrary window, for the
    // calendar-period/scheduled reports, the growth forecast's monthly history, and the
    // anomaly check's daily baseline. Half-open [start, end), not Between - Anthony's
    // review: Between is inclusive on both ends, double-counting a row created exactly
    // at a shared window boundary
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    // PM-07 - active designers who have at least one project in this category
    @Query("select count(distinct p.designer.id) from Project p "
            + "where p.category.id = :categoryId and p.designer.status = com.csit314.platform.model.AccountStatus.ACTIVE")
    long countDistinctActiveDesignersByCategoryId(@Param("categoryId") Long categoryId);

    // IDR-10/CUS-04/CUS-09/CUS-10/PM-05 - Browse search, filters on keyword/category/budget/room
    // size/verified/location when given. Only shows projects from ACTIVE designers - a suspended,
    // pending or rejected designer's work shouldn't be browsable.
    // CUS-04 - minBudget/maxBudget are the customer's own budget range; a project only
    // matches if its whole budget window fits inside that range, not just its max
    // PM-05 - Anthony's review on PR #27: this used to match on the designer's declared
    // serviceArea text alone, so a designer Platform Management had marked unverified for
    // that area still showed up in a location search - the opposite of what the story
    // asks for ("so customers aren't matched with designers who can't actually service
    // their location"). Now it also requires serviceAreaVerified, so an unconfirmed
    // location claim doesn't surface a designer who may not actually cover that area
    // :query and :location are escaped by the caller (BrowseService) before being passed
    // in here, so a % or _ typed into a search box is matched literally instead of acting
    // as a wildcard. The escape character is '!', not a backslash - MySQL treats a
    // backslash inside a string literal as its own escape character unless
    // NO_BACKSLASH_ESCAPES is set, so an escape '\' clause risks an unterminated literal
    // there even though H2 (what the tests run against) accepts it fine. '!' has no
    // special meaning in either database's string literals or LIKE patterns
    @Query("select p from Project p where "
            + "(:query is null or lower(p.title) like lower(concat('%', :query, '%')) escape '!' "
            + "or lower(p.description) like lower(concat('%', :query, '%')) escape '!') "
            + "and (:categoryName is null or p.category.name = :categoryName) "
            + "and (:minBudget is null or p.budgetMin >= :minBudget) "
            + "and (:maxBudget is null or p.budgetMax <= :maxBudget) "
            + "and (:roomSize is null or p.roomSizeSqm <= :roomSize) "
            + "and (:verifiedOnly = false or p.designer.verified = true) "
            + "and (:location is null or (p.designer.serviceAreaVerified = true "
            + "and lower(p.designer.company.serviceArea) like lower(concat('%', :location, '%')) escape '!')) "
            + "and p.designer.status = com.csit314.platform.model.AccountStatus.ACTIVE "
            + "order by p.createdAt desc")
    List<Project> search(@Param("query") String query, @Param("categoryName") String categoryName,
                         @Param("minBudget") Integer minBudget, @Param("maxBudget") Integer maxBudget,
                         @Param("roomSize") Integer roomSize,
                         @Param("verifiedOnly") boolean verifiedOnly, @Param("location") String location);

    // CUS-12 - only listings that actually gave a budget, to estimate a range from
    List<Project> findByCategoryNameAndBudgetMinIsNotNull(String categoryName);

    // CUS-05 - other projects in categories the customer already likes
    List<Project> findByCategoryIdInAndIdNotInOrderByCreatedAtDesc(List<Long> categoryIds, List<Long> excludeIds);

    // extra, not its own tracked story - total views across all of a category's
    // projects, so PM can see what's popular
    @Query("select coalesce(sum(p.viewCount), 0) from Project p where p.category.id = :categoryId")
    long sumViewCountByCategoryId(@Param("categoryId") Long categoryId);

    // PM-10 - designers ranked by their whole portfolio's views + shortlists, highest
    // first; Pageable caps how many come back (the service asks for a fixed top N)
    @Query("select new com.csit314.platform.dto.TopDesigner(d.id, c.name, sum(p.viewCount), sum(p.shortlistCount)) "
            + "from Project p join p.designer d join d.company c "
            + "where d.status = com.csit314.platform.model.AccountStatus.ACTIVE "
            + "group by d.id, c.name "
            + "order by sum(p.viewCount) + sum(p.shortlistCount) desc")
    List<TopDesigner> topDesigners(Pageable limit);

    // PM-10 - individual projects ranked by their own views + shortlists, highest first
    @Query("select new com.csit314.platform.dto.TopProject(p.id, p.title, c.name, p.viewCount, p.shortlistCount) "
            + "from Project p join p.designer d join d.company c "
            + "where d.status = com.csit314.platform.model.AccountStatus.ACTIVE "
            + "order by p.viewCount + p.shortlistCount desc")
    List<TopProject> topProjects(Pageable limit);
}
