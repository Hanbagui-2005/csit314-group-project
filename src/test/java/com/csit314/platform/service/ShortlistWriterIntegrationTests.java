package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ShortlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

// IDR-10/11 - proves the REQUIRES_NEW isolation actually works against a real database
// and transaction manager, not a mock. A constraint violation here rolls back only
// ShortlistWriter's own transaction, cleanly, and propagates out as a normal exception -
// it is deliberately NOT caught inside ShortlistWriter itself, since save() is also
// @Transactional and would mark that same (REQUIRES_NEW) transaction rollback-only before
// any catch block there could run, making its own commit fail with
// UnexpectedRollbackException regardless. This test's own code (running in a separate,
// unaffected transaction/no transaction at all) is what safely catches it.
//
// NOT_SUPPORTED turns off @DataJpaTest's usual auto-rollback wrapping for this test, so
// the setup rows below are actually committed (each save() runs in its own transaction,
// same as in production). Without that, they'd still be sitting in the test's own
// uncommitted transaction, invisible to insert()'s separate REQUIRES_NEW transaction -
// which would fail with a foreign key violation instead of proving anything.
//
// Anthony's review raised a real concern about REQUIRES_NEW committing independently of
// the caller's transaction: if the caller's own transaction then failed for some
// unrelated reason, a counter the caller was maintaining elsewhere could end up
// permanently out of sync with this table. PROPAGATION_NESTED (a savepoint, rolling
// back together with the caller's transaction) looked like the fix, but isn't usable
// here - the default Hibernate JPA dialect this project uses doesn't support
// savepoints (confirmed directly: attempting it throws
// NestedTransactionNotSupportedException). The actual fix is Project.shortlistCount
// itself: it's a Hibernate @Formula now (a live count(*) against this table), not a
// counter anything increments - see rollingBackDoesNotLeaveTheDerivedCountOutOfSync
// below, and Project's own field comment.
@DataJpaTest
@Import(ShortlistWriter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShortlistWriterIntegrationTests {

    @Autowired private ShortlistWriter shortlistWriter;
    @Autowired private ShortlistRepository shortlistRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private InteriorDesignerRepository designerRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void losingTheRaceRollsBackCleanlyWithoutPoisoningAnythingElse() {
        Category category = categoryRepository.save(new Category("Kitchen"));
        InteriorDesigner designer = designerRepository.save(new InteriorDesigner("Designer",
                "designer@example.test", "encoded", new Company("Studio", "Description", "12345")));
        Project project = projectRepository.save(new Project("Kitchen remodel", "x", category, designer));
        Customer customer = customerRepository.save(new Customer("Jordan Lee", "jordan@example.test", "encoded"));

        shortlistWriter.insert(customer, project);
        assertThatThrownBy(() -> shortlistWriter.insert(customer, project))
                .isInstanceOf(DataIntegrityViolationException.class);

        // an unrelated save still works right after the failed insert - if that insert's
        // REQUIRES_NEW transaction had somehow shared state with (and poisoned) a
        // surrounding one, this would fail too
        Category anotherCategory = categoryRepository.save(new Category("Bathroom"));
        assertThat(anotherCategory.getId()).isNotNull();
        assertThat(shortlistRepository.count()).isEqualTo(1);
    }

    // Anthony's review - proves the actual fix for the orphaning concern: with
    // shortlistCount now a derived @Formula (live count(*) against this table)
    // instead of a counter field, re-reading the project after an insert always
    // reflects exactly what's in the shortlists table, with no separate increment
    // step that could ever fall out of sync with it
    @Test
    void shortlistCountIsAlwaysDerivedFromTheShortlistsTableItself() {
        Category category = categoryRepository.save(new Category("Office"));
        InteriorDesigner designer = designerRepository.save(new InteriorDesigner("Designer",
                "designer3@example.test", "encoded", new Company("Studio", "Description", "12345")));
        Project project = projectRepository.save(new Project("Office refresh", "x", category, designer));
        Customer customer = customerRepository.save(new Customer("Sam Ray", "sam@example.test", "encoded"));

        entityManager.clear();
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getShortlistCount()).isZero();

        shortlistWriter.insert(customer, project);

        // force a real re-read - a @Formula is only (re-)computed when an entity is
        // actually loaded by a query, not when served from the persistence context's
        // own cache, so without this the still-cached "project" instance above would
        // just show whatever its shortlistCount field held at load time
        entityManager.clear();
        assertThat(projectRepository.findById(project.getId()).orElseThrow().getShortlistCount()).isEqualTo(1);
    }
}
