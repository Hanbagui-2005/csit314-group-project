package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.ShortlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// IDR-10/11 - the java-level save call. The REQUIRES_NEW transaction isolation this
// class exists for can't be proven by a mocked unit test like this one - that's what
// ShortlistWriterIntegrationTests is for
@ExtendWith(MockitoExtension.class)
class ShortlistWriterUnitTests {

    @Mock ShortlistRepository shortlistRepository;
    ShortlistWriter writer;

    Customer customer;
    Project project;

    @BeforeEach
    void setUp() {
        writer = new ShortlistWriter(shortlistRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        InteriorDesigner designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        Category category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void savesTheShortlist() {
        writer.insert(customer, project);

        verify(shortlistRepository).save(any(Shortlist.class));
    }

    // deliberately not caught here - see the class-level note on why that only works
    // from outside this method's own (REQUIRES_NEW) transaction
    @Test
    void letsAConstraintViolationPropagate() {
        when(shortlistRepository.save(any(Shortlist.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> writer.insert(customer, project))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
