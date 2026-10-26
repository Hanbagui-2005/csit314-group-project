package com.csit314.platform.service;

import com.csit314.platform.model.Project;
import com.csit314.platform.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// IDR-10 - public browsing + the real view-count tracking that was missing
@Service
public class BrowseService {

    private final ProjectRepository projectRepository;

    public BrowseService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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
        return project;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
