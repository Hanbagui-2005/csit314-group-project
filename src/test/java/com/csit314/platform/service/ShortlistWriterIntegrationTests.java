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
}
