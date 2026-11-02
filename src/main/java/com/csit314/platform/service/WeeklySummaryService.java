package com.csit314.platform.service;

import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.MessageRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import com.csit314.platform.repository.ShortlistRepository;
import com.csit314.platform.web.dto.WeeklySummary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// IDR-08 - pulls together IDR-10/11/12/13's data into one weekly snapshot
@Service
public class WeeklySummaryService {

    private final ProjectRepository projectRepository;
    private final ProjectViewRepository projectViewRepository;
    private final ShortlistRepository shortlistRepository;
    private final MessageRepository messageRepository;

    public WeeklySummaryService(ProjectRepository projectRepository,
                                ProjectViewRepository projectViewRepository,
                                ShortlistRepository shortlistRepository,
                                MessageRepository messageRepository) {
        this.projectRepository = projectRepository;
        this.projectViewRepository = projectViewRepository;
        this.shortlistRepository = shortlistRepository;
        this.messageRepository = messageRepository;
    }

    public WeeklySummary summarise(InteriorDesigner designer) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Project> projects = projectRepository.findByDesignerIdOrderByCreatedAtDesc(designer.getId());

        long viewsThisWeek = projectViewRepository.countByProjectDesignerIdAndViewedAtAfter(designer.getId(), weekAgo);
        long shortlistsThisWeek = shortlistRepository.countByProjectDesignerIdAndCreatedAtAfter(designer.getId(), weekAgo);
        long messagesThisWeek = messageRepository.countByRecipientIdAndSentAtAfter(designer.getId(), weekAgo);

        int totalViews = projects.stream().mapToInt(Project::getViewCount).sum();
        int totalShortlists = projects.stream().mapToInt(Project::getShortlistCount).sum();

        return new WeeklySummary(viewsThisWeek, shortlistsThisWeek, messagesThisWeek,
                projects.size(), totalViews, totalShortlists);
    }
}
