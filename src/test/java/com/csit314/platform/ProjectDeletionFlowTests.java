package com.csit314.platform;

import com.csit314.platform.model.Booking;
import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.PhotoHash;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.ProjectFlag;
import com.csit314.platform.model.ProjectView;
import com.csit314.platform.model.Shortlist;
import com.csit314.platform.repository.BookingRepository;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.CustomerRepository;
import com.csit314.platform.repository.InteriorDesignerRepository;
import com.csit314.platform.repository.PhotoHashRepository;
import com.csit314.platform.repository.ProjectFlagRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import com.csit314.platform.repository.ShortlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// designer deleting a project, and removing a single photo from one - end to end against
// the real (H2) schema, so the foreign-key cleanup is actually exercised
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectDeletionFlowTests {

    private static final String PASSWORD = "Designer!2026";

    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProjectRepository projectRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InteriorDesignerRepository designerRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ShortlistRepository shortlistRepository;
    @Autowired ProjectViewRepository projectViewRepository;
    @Autowired ProjectFlagRepository projectFlagRepository;
    @Autowired PhotoHashRepository photoHashRepository;
    @Autowired BookingRepository bookingRepository;

    @Test
    void designerCanDeleteAProjectThatHasShortlistsViewsFlagsAndBookings() throws Exception {
        InteriorDesigner designer = newDesigner();
        Project project = newProject(designer);
        Customer customer = customerRepository.save(new Customer("Shopper",
                "shopper-" + UUID.randomUUID() + "@example.test", "unused"));
        shortlistRepository.save(new Shortlist(customer, project));
        projectViewRepository.save(new ProjectView(project));
        projectFlagRepository.save(new ProjectFlag(project, customer, "Looks copied"));
        photoHashRepository.save(new PhotoHash(project, "/uploads/delete-test-after.jpg", 12345L));
        Booking booking = bookingRepository.save(
                new Booking(customer, designer, project, LocalDate.now().plusDays(7), "About this project"));
        MockHttpSession session = loginDesigner(designer);
        // a real request starts with an empty persistence context - without this, the
        // shortlist/booking objects created above would still be cached here pointing at
        // the project, which never happens outside a test
        entityManager.flush();
        entityManager.clear();

        mvc.perform(post("/designer/projects/" + project.getId() + "/delete")
                        .with(csrf()).session(session))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andExpect(flash().attribute("successMessage", "\"Deletable project\" has been deleted."));

        entityManager.flush();
        entityManager.clear();
        assertThat(projectRepository.findById(project.getId())).isEmpty();
        assertThat(shortlistRepository.existsByCustomerIdAndProjectId(customer.getId(), project.getId())).isFalse();
        assertThat(projectViewRepository.findAll()).noneMatch(v -> v.getProject().getId().equals(project.getId()));
        assertThat(projectFlagRepository.findAll()).noneMatch(f -> f.getProject().getId().equals(project.getId()));
        assertThat(photoHashRepository.existsByPhotoUrl("/uploads/delete-test-after.jpg")).isFalse();
        // the booking is a real request from a customer, so it's kept - just without the project link
        Booking kept = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(kept.getProject()).isNull();
    }

    @Test
    void anotherDesignerCannotDeleteSomeoneElsesProject() throws Exception {
        Project project = newProject(newDesigner());
        InteriorDesigner intruder = newDesigner();

        mvc.perform(post("/designer/projects/" + project.getId() + "/delete")
                        .with(csrf()).session(loginDesigner(intruder)))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andExpect(flash().attribute("errorMessage", "You can only manage your own projects."));

        assertThat(projectRepository.findById(project.getId())).isPresent();
    }

    @Test
    void designerCanRemoveOnePhotoButNotTheLastAfterPhoto() throws Exception {
        InteriorDesigner designer = newDesigner();
        Project project = newProject(designer);
        project.addPhoto("/uploads/delete-test-after-2.jpg");
        projectRepository.saveAndFlush(project);
        MockHttpSession session = loginDesigner(designer);
        String deleteUrl = "/designer/projects/" + project.getId() + "/photos/delete";

        mvc.perform(post(deleteUrl).with(csrf()).session(session)
                        .param("url", "/uploads/delete-test-after.jpg").param("kind", "after"))
                .andExpect(redirectedUrl("/designer/projects/" + project.getId() + "/edit"))
                .andExpect(flash().attribute("successMessage", "Photo removed."));
        mvc.perform(post(deleteUrl).with(csrf()).session(session)
                        .param("url", "/uploads/delete-test-before.jpg").param("kind", "before"))
                .andExpect(flash().attribute("successMessage", "Photo removed."));
        // only one "after" photo left now - removing it would leave an empty project
        mvc.perform(post(deleteUrl).with(csrf()).session(session)
                        .param("url", "/uploads/delete-test-after-2.jpg").param("kind", "after"))
                .andExpect(flash().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("at least one photo")));

        entityManager.flush();
        entityManager.clear();
        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getPhotoUrls()).containsExactly("/uploads/delete-test-after-2.jpg");
        assertThat(reloaded.getBeforePhotoUrls()).isEmpty();
    }

    @Test
    void editPageShowsReplaceAndDeleteControlsForEachPhoto() throws Exception {
        InteriorDesigner designer = newDesigner();
        Project project = newProject(designer);

        String html = mvc.perform(get("/designer/projects/" + project.getId() + "/edit")
                        .session(loginDesigner(designer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("/designer/projects/" + project.getId() + "/photos/replace")
                .contains("/designer/projects/" + project.getId() + "/photos/delete")
                .contains("/designer/projects/" + project.getId() + "/delete")
                .contains("value=\"/uploads/delete-test-after.jpg\"")
                .contains("value=\"/uploads/delete-test-before.jpg\"");
    }

    private InteriorDesigner newDesigner() {
        InteriorDesigner designer = new InteriorDesigner("Delete Tester",
                "designer-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(PASSWORD),
                new Company("Delete Test Studio", "Studio used for deletion tests.", "5550000000"));
        designer.approve();
        return designerRepository.save(designer);
    }

    private Project newProject(InteriorDesigner designer) {
        Category category = categoryRepository.save(new Category("Deletion test " + UUID.randomUUID()));
        Project project = new Project("Deletable project", "A project that's about to be deleted.",
                category, designer);
        project.addPhoto("/uploads/delete-test-after.jpg");
        project.addBeforePhoto("/uploads/delete-test-before.jpg");
        return projectRepository.saveAndFlush(project);
    }

    private MockHttpSession loginDesigner(InteriorDesigner designer) throws Exception {
        return (MockHttpSession) mvc.perform(post("/designer/login")
                        .with(csrf())
                        .param("email", designer.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/designer/dashboard"))
                .andReturn().getRequest().getSession(false);
    }
}
