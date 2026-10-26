package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #22 [IDR-10] - real view counts, and the Browse search behind it. */
@ExtendWith(MockitoExtension.class)
class BrowseServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    BrowseService service;

    Project project;

    @BeforeEach
    void setUp() {
        service = new BrowseService(projectRepository);

        InteriorDesigner designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        Category category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void viewingARealProjectBumpsTheCount() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        Project result = service.viewProject(100L);

        assertThat(result.getViewCount()).isEqualTo(1);
    }

    @Test
    void viewingTwiceCountsTwice() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));

        service.viewProject(100L);
        service.viewProject(100L);

        assertThat(project.getViewCount()).isEqualTo(2);
    }

    @Test
    void viewingAMissingProjectFails() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.viewProject(999L))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void blankSearchTermsAreTreatedAsNoFilter() {
        service.search("  ", "");

        verify(projectRepository).search(null, null);
    }

    @Test
    void realSearchTermsArePassedThrough() {
        service.search(" kitchen ", "Kitchen");

        verify(projectRepository).search("kitchen", "Kitchen");
    }
}
