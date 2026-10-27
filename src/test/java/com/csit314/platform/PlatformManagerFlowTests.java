package com.csit314.platform;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// PM-01 through PM-05 - controller/routing coverage for the Platform Management portal
@SpringBootTest
@AutoConfigureMockMvc
class PlatformManagerFlowTests {

    private static final String PM_EMAIL = "platform-manager@test.local";
    private static final String PM_PASSWORD = "Manager@12345";
    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private InteriorDesignerRepository designerRepository;

    @Test
    void platformManagerCanLogInAndReachCategories() throws Exception {
        MockHttpSession session = loginPlatformManager();
        mvc.perform(get("/platform-manager/categories").session(session)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndWrongPortalCredentialsAreRejected() throws Exception {
        mvc.perform(post("/platform-manager/login").param("email", PM_EMAIL).param("password", "nope"))
                .andExpect(redirectedUrl("/platform-manager/login"))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));

        // admin credentials don't work on the platform manager portal, and vice versa
        mvc.perform(post("/platform-manager/login").param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));
        mvc.perform(post("/admin/login").param("email", PM_EMAIL).param("password", PM_PASSWORD))
                .andExpect(flash().attribute("errorMessage", "Invalid email or password."));
    }

    @Test
    void platformManagerPagesRequirePlatformManagerLogin() throws Exception {
        mvc.perform(get("/platform-manager/categories")).andExpect(redirectedUrl("/platform-manager/login"));
        mvc.perform(get("/platform-manager/reports")).andExpect(redirectedUrl("/platform-manager/login"));

        // an admin session can't use the platform manager's pages, and vice versa
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(post("/admin/login")
                        .param("email", ADMIN_EMAIL).param("password", ADMIN_PASSWORD))
                .andExpect(redirectedUrl("/admin/users"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/platform-manager/categories").session(adminSession))
                .andExpect(redirectedUrl("/platform-manager/login"));

        MockHttpSession pmSession = loginPlatformManager();
        mvc.perform(get("/admin/users").session(pmSession)).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void addRenameAndDeleteCategory() throws Exception {
        MockHttpSession session = loginPlatformManager();

        mvc.perform(post("/platform-manager/categories").session(session).param("name", "  Sunrooms  "))
                .andExpect(redirectedUrl("/platform-manager/categories"))
                .andExpect(flash().attribute("successMessage", "Category added."));
        Category category = categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals("Sunrooms"))
                .findFirst().orElseThrow();

        // a blank name and a duplicate name are both rejected
        mvc.perform(post("/platform-manager/categories").session(session).param("name", "   "))
                .andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/platform-manager/categories").session(session).param("name", "Sunrooms"))
                .andExpect(flash().attribute("errorMessage", "A category with that name already exists."));

        mvc.perform(post("/platform-manager/categories/" + category.getId() + "/rename")
                        .session(session).param("name", "Sun Rooms"))
                .andExpect(flash().attribute("successMessage", "Category renamed."));
        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getName()).isEqualTo("Sun Rooms");

        mvc.perform(post("/platform-manager/categories/" + category.getId() + "/delete").session(session))
                .andExpect(flash().attribute("successMessage", "Category deleted."));
        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    @Test
    void categoryStillUsedByAProjectCannotBeDeleted() throws Exception {
        MockHttpSession session = loginPlatformManager();

        Category category = categoryRepository.save(new Category("Verification-only category"));
        Company company = new Company("Verification Studio", "Testing only", "+61 400 000 001");
        InteriorDesigner designer = designerRepository.save(
                new InteriorDesigner("Designer", "verification.designer@test.local", "encoded", company));
        Project project = new Project("Guard test project", "Used to verify PM-04", category, designer);
        project.addPhoto("/uploads/guard-test-project.jpg");
        projectRepository.save(project);

        mvc.perform(post("/platform-manager/categories/" + category.getId() + "/delete").session(session))
                .andExpect(flash().attribute("errorMessage",
                        "This category is still used by at least one project and can't be deleted."));
        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void reportsPageShowsAllThreePeriods() throws Exception {
        MockHttpSession session = loginPlatformManager();
        mvc.perform(get("/platform-manager/reports").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today")))
                .andExpect(content().string(containsString("This week")))
                .andExpect(content().string(containsString("This month")));
    }

    private MockHttpSession loginPlatformManager() throws Exception {
        return (MockHttpSession) mvc.perform(post("/platform-manager/login")
                        .param("email", PM_EMAIL).param("password", PM_PASSWORD))
                .andExpect(redirectedUrl("/platform-manager/categories"))
                .andReturn().getRequest().getSession(false);
    }
}
