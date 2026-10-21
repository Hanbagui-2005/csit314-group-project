package com.csit314.platform.qa;

import com.csit314.platform.model.Customer;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.web.dto.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Executable acceptance checks for the pagination part of Taiga #95. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:qa_pagination;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class Task095PaginationRequirementsTests {
    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    private MockHttpSession admin;

    @BeforeEach
    void seedMoreUsersThanTheRequestedPageSize() throws Exception {
        // Password hashes are fixtures; these customers are never authenticated.
        for (int i = 1; i <= 20; i++) {
            customers.save(new Customer("Pagination Customer " + i,
                    "qa.pagination" + i + "@example.test", "unused-test-hash"));
        }
        customers.flush();
        admin = (MockHttpSession) mvc.perform(post("/admin/login")
                        .param("email", "admin@test.local").param("password", "Admin@12345"))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
    }

    private List<UserSummary> page(int number) throws Exception {
        var result = mvc.perform(get("/admin/users").session(admin)
                        .param("page", Integer.toString(number)).param("size", "5"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("users")).andReturn();
        Object rows = result.getModelAndView().getModel().get("users");
        assertThat(rows).isInstanceOf(List.class);
        return ((List<?>) rows).stream().map(UserSummary.class::cast).toList();
    }

    @Test
    @DisplayName("#95 An explicit page size limits the number of returned user summaries")
    void explicitPageSizeLimitsResults() throws Exception {
        assertThat(page(1))
                .as("Task #95: size=5 must return five rows when more than five users exist")
                .hasSize(5);
    }

    @Test
    @DisplayName("#95 Changing the page returns a different, non-overlapping set of user summaries")
    void differentPagesReturnDifferentUsers() throws Exception {
        List<Long> first = page(1).stream().map(UserSummary::id).toList();
        List<Long> second = page(2).stream().map(UserSummary::id).toList();
        assertThat(first).as("Task #95: consecutive pages must not repeat the complete user list")
                .doesNotContainAnyElementsOf(second);
    }
}
