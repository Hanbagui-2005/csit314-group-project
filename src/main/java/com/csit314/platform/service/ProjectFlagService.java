package com.csit314.platform.service;

import com.csit314.platform.model.Customer;
import com.csit314.platform.model.Project;
import com.csit314.platform.model.ProjectFlag;
import com.csit314.platform.repository.ProjectFlagRepository;
import com.csit314.platform.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// PM-11 - projects flagged or reported for Platform Management to review
@Service
public class ProjectFlagService {

    private static final int MAX_REASON_LENGTH = 1000;

    private final ProjectFlagRepository projectFlagRepository;
    private final ProjectRepository projectRepository;

    public ProjectFlagService(ProjectFlagRepository projectFlagRepository, ProjectRepository projectRepository) {
        this.projectFlagRepository = projectFlagRepository;
        this.projectRepository = projectRepository;
    }

    // a customer reporting a project they think is inappropriate
    @Transactional
    public ProjectFlag report(Customer customer, Long projectId, String reason) {
        String trimmedReason = InputRules.requireText(reason, "Reason", MAX_REASON_LENGTH);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("That project couldn't be found."));
        return projectFlagRepository.save(new ProjectFlag(project, customer, trimmedReason));
    }

    // PM-04 - the system itself reporting a project, instead of a customer
    @Transactional
    public ProjectFlag flagAutomatically(Project project, String reason) {
        return projectFlagRepository.save(new ProjectFlag(project, null, reason));
    }

    @Transactional(readOnly = true)
    public List<ProjectFlag> all() {
        return projectFlagRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void review(Long flagId) {
        try {
            find(flagId).review();
        } catch (IllegalStateException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    @Transactional
    public void dismiss(Long flagId) {
        try {
            find(flagId).dismiss();
        } catch (IllegalStateException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    private ProjectFlag find(Long flagId) {
        return projectFlagRepository.findById(flagId)
                .orElseThrow(() -> new ServiceException("Flag not found."));
    }
}
