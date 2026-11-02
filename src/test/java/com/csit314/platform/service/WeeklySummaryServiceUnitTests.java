package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.Company;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.MessageRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.web.dto.WeeklySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Taiga #20 [IDR-08] - weekly summary, now that views/shortlists/messages are all real. */
@ExtendWith(MockitoExtension.class)
class WeeklySummaryServiceUnitTests {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectViewRepository projectViewRepository;
    @Mock ShortlistRepository shortlistRepository;
    @Mock MessageRepository messageRepository;
    WeeklySummaryService service;

    InteriorDesigner designer;

    @BeforeEach
    void setUp() {
        service = new WeeklySummaryService(projectRepository, projectViewRepository, shortlistRepository, messageRepository);

        designer = new InteriorDesigner("Designer", "designer@example.test", "encoded",
                new Company("Studio", "Description", "12345"));
        ReflectionTestUtils.setField(designer, "id", 2L);
    }

    @Test
    void addsUpThisWeeksActivityAndAllTimeTotals() {
        Category category = new Category("Kitchen");
        Project project1 = new Project("Kitchen remodel", "Description", category, designer);
        ReflectionTestUtils.setField(project1, "viewCount", 10);
        ReflectionTestUtils.setField(project1, "shortlistCount", 3);
        Project project2 = new Project("Bathroom refresh", "Description", category, designer);
        ReflectionTestUtils.setField(project2, "viewCount", 5);
        ReflectionTestUtils.setField(project2, "shortlistCount", 1);

        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(project1, project2));
        when(projectViewRepository.countByProjectDesignerIdAndViewedAtAfter(eq(2L), any())).thenReturn(7L);
        when(shortlistRepository.countByProjectDesignerIdAndCreatedAtAfter(eq(2L), any())).thenReturn(2L);
        when(messageRepository.countByRecipientIdAndSentAtAfter(eq(2L), any())).thenReturn(4L);

        WeeklySummary summary = service.summarise(designer);

        assertThat(summary.viewsThisWeek()).isEqualTo(7);
        assertThat(summary.shortlistsThisWeek()).isEqualTo(2);
        assertThat(summary.messagesThisWeek()).isEqualTo(4);
        assertThat(summary.totalProjects()).isEqualTo(2);
        assertThat(summary.totalViewsAllTime()).isEqualTo(15);
        assertThat(summary.totalShortlistsAllTime()).isEqualTo(4);
    }

    @Test
    void aDesignerWithNoProjectsGetsAllZeroes() {
        when(projectRepository.findByDesignerIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());
        when(projectViewRepository.countByProjectDesignerIdAndViewedAtAfter(anyLong(), any())).thenReturn(0L);
        when(shortlistRepository.countByProjectDesignerIdAndCreatedAtAfter(anyLong(), any())).thenReturn(0L);
        when(messageRepository.countByRecipientIdAndSentAtAfter(anyLong(), any())).thenReturn(0L);

        WeeklySummary summary = service.summarise(designer);

        assertThat(summary.totalProjects()).isZero();
        assertThat(summary.totalViewsAllTime()).isZero();
    }
}
