package com.csit314.platform.qa;

import com.csit314.platform.model.AccountStatus;
import com.csit314.platform.model.Company;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.service.CustomerService;
import com.csit314.platform.service.DesignerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.AfterTransaction;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Real registration-service and H2 persistence verification; transactions roll back after each test. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:qa_dataset;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
class LargeMockDatasetTests {
    @Autowired CustomerService customerService;
    @Autowired DesignerService designerService;
    @Autowired CustomerRepository customers;
    @Autowired InteriorDesignerRepository designers;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    private long customerBaseline;
    private long designerBaseline;
    private long companyBaseline;
    private String rollbackEvidenceName;

    @BeforeTransaction
    void recordCountsBeforeTransaction() {
        customerBaseline = customers.count();
        designerBaseline = designers.count();
        companyBaseline = entityManager.createQuery("select count(c) from Company c", Long.class).getSingleResult();
    }

    @AfterTransaction
    void verifyGeneratedRecordsWereRolledBack() throws Exception {
        long customerCount = customers.count();
        long designerCount = designers.count();
        long companyCount = entityManager.createQuery("select count(c) from Company c", Long.class).getSingleResult();
        assertThat(customerCount).isEqualTo(customerBaseline);
        assertThat(designerCount).isEqualTo(designerBaseline);
        assertThat(companyCount).isEqualTo(companyBaseline);
        evidence(rollbackEvidenceName, Map.of("database", "H2", "rollbackVerified", true,
                "customerCountAfterRollback", customerCount, "customerBaseline", customerBaseline,
                "designerCountAfterRollback", designerCount, "designerBaseline", designerBaseline,
                "companyCountAfterRollback", companyCount, "companyBaseline", companyBaseline));
    }

    private void evidence(String filename, Map<String, Object> facts) throws Exception {
        Files.createDirectories(Path.of("qa-evidence", "dataset"));
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(Path.of("qa-evidence", "dataset", filename).toFile(), facts);
    }
    @Test void oneHundredCustomersAreValidatedHashedAndPersisted() throws Exception {
        rollbackEvidenceName = "customers-rollback.json";
        var forms = MockDataGenerator.customers(100);
        long before = customers.count();
        for (var form : forms) customerService.register(form);
        customers.flush();
        var saved = customers.findAll();
        assertThat(customers.count() - before).isEqualTo(100);
        assertThat(saved.stream().map(u -> u.getEmail()).toList()).doesNotHaveDuplicates();
        assertThat(saved).allSatisfy(u -> {
            assertThat(u.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(u.getPasswordHash()).startsWith("$2");
        });
        for (var form : forms) {
            var customer = saved.stream().filter(u -> u.getEmail().equals(form.email())).findFirst().orElseThrow();
            assertThat(encoder.matches(form.password(), customer.getPasswordHash())).isTrue();
        }
        MockDataGenerator.export(Path.of("qa-data"), 100);
        evidence("customers.json", Map.of("task", "#115", "requested", 100, "persistedCustomers", customers.count() - before,
                "database", "H2", "allPasswordsEncoded", true, "allPasswordHashesVerified", true,
                "rollbackAfterTest", true));
    }
    @Test void oneHundredDesignersAndCompaniesArePersistedWithValidRelationships() throws Exception {
        rollbackEvidenceName = "designers-rollback.json";
        long before = designers.count();
        long companiesBefore = entityManager.createQuery("select count(c) from Company c", Long.class).getSingleResult();
        var forms = MockDataGenerator.designers(100);
        for (var form : forms) designerService.register(form);
        designers.flush();
        var saved = designers.findAll();
        long companyCount = entityManager.createQuery("select count(c) from Company c", Long.class).getSingleResult() - companiesBefore;
        assertThat(designers.count() - before).isEqualTo(100);
        assertThat(companyCount).isEqualTo(100);
        assertThat(saved.stream().map(u -> u.getCompany().getId()).toList()).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(saved).allSatisfy(u -> {
            assertThat(u.getStatus()).isEqualTo(AccountStatus.PENDING);
            assertThat(u.getCompany().getName()).startsWith("QA Design Company");
            assertThat(u.getPasswordHash()).startsWith("$2");
        });
        for (var form : forms) {
            var designer = saved.stream().filter(u -> u.getEmail().equals(form.email())).findFirst().orElseThrow();
            assertThat(encoder.matches(form.password(), designer.getPasswordHash())).isTrue();
            assertThat(designer.getContactName()).isEqualTo(form.contactName());
            assertThat(designer.getCompany().getName()).isEqualTo(form.companyName());
            assertThat(designer.getCompany().getDescription()).isEqualTo(form.companyDescription());
            assertThat(designer.getCompany().getPhone()).isEqualTo(form.companyPhone());
        }
        MockDataGenerator.export(Path.of("qa-data"), 100);
        evidence("designers.json", Map.of("task", "#107", "requested", 100, "persistedDesigners", designers.count() - before,
                "persistedCompanies", companyCount, "oneCompanyPerDesigner", true, "database", "H2",
                "allPasswordsEncoded", true, "allPasswordHashesVerified", true, "rollbackAfterTest", true));
    }
}
