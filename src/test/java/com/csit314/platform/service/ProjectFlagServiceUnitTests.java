package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.Customer;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.ProjectFlag;
import com.csit314.platform.model.ProjectFlagStatus;
import com.csit314.platform.repository.ProjectFlagRepository;
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

// PM-11
@ExtendWith(MockitoExtension.class)
class ProjectFlagServiceUnitTests {

    @Mock ProjectFlagRepository projectFlagRepository;
    @Mock ProjectRepository projectRepository;
    ProjectFlagService service;

    Customer customer;
    Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectFlagService(projectFlagRepository, projectRepository);

        customer = new Customer("Jordan Lee", "jordan@example.test", "encoded");
        ReflectionTestUtils.setField(customer, "id", 1L);

        InteriorDesigner designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        Category category = new Category("Kitchen");
        project = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project, "id", 100L);
    }

    @Test
    void reportSavesAnOpenFlagAgainstTheProject() {
        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectFlagRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectFlag result = service.report(customer, 100L, "Looks plagiarized");

        assertThat(result.getProject()).isEqualTo(project);
        assertThat(result.getReportedBy()).isEqualTo(customer);
        assertThat(result.getStatus()).isEqualTo(ProjectFlagStatus.OPEN);
    }

    @Test
    void reportRejectsABlankReason() {
        assertThatThrownBy(() -> service.report(customer, 100L, "  ")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(projectRepository, projectFlagRepository);
    }

    @Test
    void reportFailsWhenProjectDoesNotExist() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(customer, 404L, "Reason"))
                .isInstanceOf(ServiceException.class);
    }

    // PM-04 - the system raises its own flags with no reporting customer
    @Test
    void flagAutomaticallySavesAFlagWithNoReportingCustomer() {
        when(projectFlagRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectFlag result = service.flagAutomatically(project, "Possible plagiarism match");

        assertThat(result.getReportedBy()).isNull();
        assertThat(result.getReason()).isEqualTo("Possible plagiarism match");
    }

    @Test
    void reviewMarksAnOpenFlagReviewed() {
        ProjectFlag flag = new ProjectFlag(project, customer, "Reason");
        ReflectionTestUtils.setField(flag, "id", 10L);
        when(projectFlagRepository.findById(10L)).thenReturn(Optional.of(flag));

        service.review(10L);

        assertThat(flag.getStatus()).isEqualTo(ProjectFlagStatus.REVIEWED);
    }

    @Test
    void dismissMarksAnOpenFlagDismissed() {
        ProjectFlag flag = new ProjectFlag(project, customer, "Reason");
        ReflectionTestUtils.setField(flag, "id", 10L);
        when(projectFlagRepository.findById(10L)).thenReturn(Optional.of(flag));

        service.dismiss(10L);

        assertThat(flag.getStatus()).isEqualTo(ProjectFlagStatus.DISMISSED);
    }

    // an admin shouldn't be able to flip a decided flag back and forth
    @Test
    void anAlreadyDismissedFlagCannotBeReviewed() {
        ProjectFlag flag = new ProjectFlag(project, customer, "Reason");
        ReflectionTestUtils.setField(flag, "id", 10L);
        when(projectFlagRepository.findById(10L)).thenReturn(Optional.of(flag));
        service.dismiss(10L);

        assertThatThrownBy(() -> service.review(10L)).isInstanceOf(ServiceException.class);
        assertThat(flag.getStatus()).isEqualTo(ProjectFlagStatus.DISMISSED);
    }

    @Test
    void reviewingAMissingFlagFails() {
        when(projectFlagRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.review(404L)).isInstanceOf(ServiceException.class);
    }
}
