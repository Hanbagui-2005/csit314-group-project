package com.csit314.platform.repository;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.*;

// IDR-10 / code review doc - "updating a project's view count may overwrite concurrent
// milestone or budget changes". Runs against a real database: the count is now an atomic
// "+ 1" update, and Project.viewCount is never written by an ordinary entity flush, so a
// stale copy of the project (e.g. a designer's edit loaded before some page views) can't
// write an old count back, and a view can't write old milestone/budget values back
@DataJpaTest
class ProjectViewCountIntegrationTests {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InteriorDesignerRepository designerRepository;
    @Autowired private TestEntityManager entityManager;

    private Long saveProject() {
        Category category = categoryRepository.save(new Category("Kitchen"));
        InteriorDesigner designer = designerRepository.save(new InteriorDesigner(
                "Contact", "designer-" + System.nanoTime() + "@test.local", "encoded",
                new Company("Studio", "Description", "12345")));
        Long id = projectRepository.save(new Project("Kitchen", "x", category, designer)).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private int storedViewCount(Long id) {
        entityManager.clear();
        return projectRepository.findById(id).orElseThrow().getViewCount();
    }

    @Test
    void eachViewIncrementsTheStoredCountAtomically() {
        Long id = saveProject();

        assertThat(projectRepository.incrementViewCount(id)).isEqualTo(1);
        projectRepository.incrementViewCount(id);

        assertThat(storedViewCount(id)).isEqualTo(2);
    }

    @Test
    void aStaleCopySavingAMilestoneChangeDoesNotOverwriteViewsCountedSinceItWasLoaded() {
        Long id = saveProject();
        Project designersCopy = projectRepository.findById(id).orElseThrow();
        assertThat(designersCopy.getViewCount()).isZero();

        projectRepository.incrementViewCount(id);
        projectRepository.incrementViewCount(id);
        projectRepository.incrementViewCount(id);

        designersCopy.advanceMilestone();
        entityManager.flush();

        assertThat(storedViewCount(id)).isEqualTo(3);
    }
}
