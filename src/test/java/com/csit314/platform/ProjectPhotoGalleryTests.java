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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectPhotoGalleryTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InteriorDesignerRepository designerRepository;

    @Test
    void customersCanSeeEveryAfterAndBeforePhoto() throws Exception {
        Project project = newProject();

        project.addPhoto("/uploads/gallery-after-1.jpg");
        project.addPhoto("/uploads/gallery-after-2.jpg");
        project.addPhoto("/uploads/gallery-after-3.jpg");

        project.addBeforePhoto("/uploads/gallery-before-1.jpg");
        project.addBeforePhoto("/uploads/gallery-before-2.jpg");

        project = projectRepository.saveAndFlush(project);

        String html = mvc.perform(get("/projects/" + project.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("project-detail"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("After renovation")
                .contains("Before renovation")
                .contains("src=\"/uploads/gallery-after-1.jpg\"")
                .contains("src=\"/uploads/gallery-after-2.jpg\"")
                .contains("src=\"/uploads/gallery-after-3.jpg\"")
                .contains("src=\"/uploads/gallery-before-1.jpg\"")
                .contains("src=\"/uploads/gallery-before-2.jpg\"");

        assertThat(html)
                .contains("href=\"/uploads/gallery-after-1.jpg\"")
                .contains("href=\"/uploads/gallery-after-2.jpg\"")
                .contains("href=\"/uploads/gallery-after-3.jpg\"")
                .contains("href=\"/uploads/gallery-before-1.jpg\"")
                .contains("href=\"/uploads/gallery-before-2.jpg\"");
    }

    @Test
    void projectsWithoutBeforePhotosStillRenderNormally() throws Exception {
        Project project = newProject();
        project.addPhoto("/uploads/gallery-after-only.jpg");

        project = projectRepository.saveAndFlush(project);

        String html = mvc.perform(get("/projects/" + project.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("project-detail"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("After renovation")
                .contains("src=\"/uploads/gallery-after-only.jpg\"")
                .contains("href=\"/uploads/gallery-after-only.jpg\"")
                .doesNotContain("id=\"before-photos-heading\"")
                .doesNotContain("Before renovation");
    }

    private Project newProject() {
        Category category = categoryRepository.save(
                new Category("Gallery test category")
        );

        Company company = new Company(
                "Gallery Test Studio",
                "Company used for gallery tests.",
                "5550000000"
        );

        InteriorDesigner designer = new InteriorDesigner(
                "Gallery Designer",
                "gallery.designer@example.test",
                "unused-test-password-hash",
                company
        );

        designer.approve();
        designer = designerRepository.save(designer);

        return new Project(
                "Gallery test project",
                "Project used to verify public photo display.",
                category,
                designer
        );
    }
}
