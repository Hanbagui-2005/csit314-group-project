package com.csit314.platform.service;

import com.csit314.platform.model.Project;
import com.csit314.platform.model.ProjectView;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// IDR-10 - public browsing + the real view-count tracking that was missing
@Service
public class BrowseService {

    private final ProjectRepository projectRepository;
    private final ProjectViewRepository projectViewRepository;

    public BrowseService(ProjectRepository projectRepository, ProjectViewRepository projectViewRepository) {
        this.projectRepository = projectRepository;
        this.projectViewRepository = projectViewRepository;
    }

    public List<Project> search(String query, String categoryName) {
        String cleanQuery = blankToNull(query);
        String cleanCategory = blankToNull(categoryName);
        return projectRepository.search(cleanQuery, cleanCategory);
    }

    @Transactional
    public Project viewProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("That project couldn't be found."));
        project.incrementViewCount();
        // IDR-08 - also logged as its own row so a weekly delta is possible later
        projectViewRepository.save(new ProjectView(project));
        return project;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
