package com.csit314.platform.repository;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

// IDR-10/CUS-04/CUS-09/CUS-10 - runs ProjectRepository.search against a real database.
// BrowseServiceUnitTests only checks that filters are passed through, which is how a
// backwards room-size comparison and a missing designer-status filter both shipped unnoticed.
@DataJpaTest
class ProjectRepositorySearchIntegrationTests {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InteriorDesignerRepository designerRepository;

    private InteriorDesigner saveDesigner(AccountStatus status) {
        return saveDesigner(status, null);
    }

    // PM-05 - matches a real customer search, so the designer's area has to be
    // Platform-Management-verified to show up in a location search; see saveDesigner(status,
    // serviceArea, serviceAreaVerified) for tests that specifically cover the unverified case
    private InteriorDesigner saveDesigner(AccountStatus status, String serviceArea) {
        return saveDesigner(status, serviceArea, serviceArea != null);
    }

    private InteriorDesigner saveDesigner(AccountStatus status, String serviceArea, boolean serviceAreaVerified) {
        Company company = new Company("Studio", "Description", "12345");
        company.setServiceArea(serviceArea);
        InteriorDesigner designer = new InteriorDesigner("Contact", "designer-" + System.nanoTime() + "@test.local",
                "encoded", company);
        designer = designerRepository.save(designer);
        ReflectionTestUtils.setField(designer, "status", status);
        designer.setServiceAreaVerified(serviceAreaVerified);
        return designerRepository.save(designer);
    }

    @Test
    void roomSizeFilterKeepsProjectsAtOrBelowTheCustomersOwnRoomSize() {
        Category category = categoryRepository.save(new Category("Kitchen"));
        InteriorDesigner active = saveDesigner(AccountStatus.ACTIVE);

        Project rightSized = new Project("Small kitchen", "x", category, active);
        ReflectionTestUtils.setField(rightSized, "roomSizeSqm", 15);
        projectRepository.save(rightSized);

        Project tooBig = new Project("Huge kitchen", "x", category, active);
        ReflectionTestUtils.setField(tooBig, "roomSizeSqm", 40);
        projectRepository.save(tooBig);

        List<Project> results = projectRepository.search(null, null, null, null, 20, false, null);

        assertThat(results).extracting(Project::getTitle)
                .contains("Small kitchen")
                .doesNotContain("Huge kitchen");
    }

    // CUS-04 - a project only matches when its whole budget window fits inside the
    // customer's own [minBudget, maxBudget] range, not just when its ceiling is low enough
    @Test
    void budgetRangeFilterKeepsOnlyProjectsWhoseWholeBudgetWindowFitsInside() {
        Category category = categoryRepository.save(new Category("Kitchen"));
        InteriorDesigner active = saveDesigner(AccountStatus.ACTIVE);

        Project withinRange = new Project("Mid-range kitchen", "x", category, active);
        ReflectionTestUtils.setField(withinRange, "budgetMin", 6000);
        ReflectionTestUtils.setField(withinRange, "budgetMax", 9000);
        projectRepository.save(withinRange);

        Project tooCheap = new Project("Budget kitchen", "x", category, active);
        ReflectionTestUtils.setField(tooCheap, "budgetMin", 1000);
        ReflectionTestUtils.setField(tooCheap, "budgetMax", 3000);
        projectRepository.save(tooCheap);

        Project tooExpensive = new Project("Premium kitchen", "x", category, active);
        ReflectionTestUtils.setField(tooExpensive, "budgetMin", 12000);
        ReflectionTestUtils.setField(tooExpensive, "budgetMax", 20000);
        projectRepository.save(tooExpensive);

        List<Project> results = projectRepository.search(null, null, 5000, 10000, null, false, null);

        assertThat(results).extracting(Project::getTitle)
                .contains("Mid-range kitchen")
                .doesNotContain("Budget kitchen", "Premium kitchen");
    }

    @Test
    void excludesProjectsFromDesignersWhoArentActive() {
        Category category = categoryRepository.save(new Category("Bathroom"));
        projectRepository.save(new Project("From an active designer", "x", category, saveDesigner(AccountStatus.ACTIVE)));
        projectRepository.save(new Project("From a suspended designer", "x", category, saveDesigner(AccountStatus.SUSPENDED)));
        projectRepository.save(new Project("From a pending designer", "x", category, saveDesigner(AccountStatus.PENDING)));

        List<Project> results = projectRepository.search(null, null, null, null, null, false, null);

        assertThat(results).extracting(Project::getTitle)
                .contains("From an active designer")
                .doesNotContain("From a suspended designer", "From a pending designer");
    }

    // PM-05 - this is what actually puts the service-area check in front of customers
    @Test
    void locationFilterKeepsOnlyProjectsFromDesignersServicingThatArea() {
        Category category = categoryRepository.save(new Category("Living Room"));
        projectRepository.save(new Project("Downtown loft", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "Downtown and Midtown")));
        projectRepository.save(new Project("Suburb house", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "Suburbs only")));

        List<Project> results = projectRepository.search(null, null, null, null, null, false, "downtown");

        assertThat(results).extracting(Project::getTitle)
                .contains("Downtown loft")
                .doesNotContain("Suburb house");
    }

    // Anthony's review on PR #27 - the location filter used to match on the declared
    // serviceArea text alone, so a designer Platform Management hadn't verified for that
    // area still showed up, the opposite of what PM-05 asks for
    @Test
    void locationFilterExcludesAnUnverifiedServiceArea() {
        Category category = categoryRepository.save(new Category("Dining Room"));
        projectRepository.save(new Project("Verified downtown studio", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "Downtown", true)));
        projectRepository.save(new Project("Unverified downtown studio", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "Downtown", false)));

        List<Project> results = projectRepository.search(null, null, null, null, null, false, "downtown");

        assertThat(results).extracting(Project::getTitle)
                .contains("Verified downtown studio")
                .doesNotContain("Unverified downtown studio");
    }

    // BrowseService escapes a literal "%" typed by the user into "100!%" before calling
    // this (the escape character is '!', not a backslash - see BrowseService.escapeLike's
    // own notes on why) - proves the query treats it as a literal character against a
    // real database, not a wildcard that would otherwise match every designer's area
    @Test
    void locationFilterTreatsAnEscapedPercentSignLiterally() {
        Category category = categoryRepository.save(new Category("Office"));
        projectRepository.save(new Project("Literal percent studio", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "100% Downtown")));
        projectRepository.save(new Project("Unrelated studio", "x", category,
                saveDesigner(AccountStatus.ACTIVE, "Somewhere else")));

        List<Project> results = projectRepository.search(null, null, null, null, null, false, "100!%");

        assertThat(results).extracting(Project::getTitle)
                .contains("Literal percent studio")
                .doesNotContain("Unrelated studio");
    }
}
