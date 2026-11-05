package com.csit314.platform.service;

import com.csit314.platform.model.Project;
import com.csit314.platform.model.ProjectView;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.repository.ProjectViewRepository;
import com.csit314.platform.dto.BudgetEstimate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// IDR-10 - public browsing + the real view-count tracking that was missing
@Service
public class BrowseService {

    private final ProjectRepository projectRepository;
    private final ProjectViewRepository projectViewRepository;

    public BrowseService(ProjectRepository projectRepository, ProjectViewRepository projectViewRepository) {
        this.projectRepository = projectRepository;
        this.projectViewRepository = projectViewRepository;
    }

    // CUS-04/CUS-09/PM-05 - minBudget/maxBudget/roomSize/verifiedOnly/location are all optional
    // filters; a project with no budget/size data of its own gets excluded once that filter
    // is in use, since there's nothing to check it against
    public List<Project> search(String query, String categoryName, Integer minBudget, Integer maxBudget,
                                Integer roomSize, boolean verifiedOnly, String location) {
        validateBudgetRange(minBudget, maxBudget);
        String cleanQuery = escapeLike(blankToNull(query));
        String cleanCategory = blankToNull(categoryName);
        String cleanLocation = escapeLike(blankToNull(location));
        return projectRepository.search(cleanQuery, cleanCategory, minBudget, maxBudget, roomSize, verifiedOnly, cleanLocation);
    }

    // Anthony's review on PR #27 - without this, a mistyped range like min 10000/max 5000
    // just silently returned an empty list with no explanation
    private void validateBudgetRange(Integer minBudget, Integer maxBudget) {
        if (minBudget != null && maxBudget != null && minBudget > maxBudget) {
            throw new ServiceException("Minimum budget can't be more than the maximum.");
        }
    }

    // IDR-10 - viewerDesignerId is the logged-in designer's own id, if any, so they can
    // browse their own project without inflating its count; countThisView is decided by
    // the caller (one view per session per project), since that's session state, not
    // something this service should track
    @Transactional
    public Project viewProject(Long projectId, Long viewerDesignerId, boolean countThisView) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("That project couldn't be found."));
        // a suspended/pending/rejected designer's project isn't browsable anywhere else
        // (search, their profile) - treat it the same way here, before the view is even
        // counted, instead of only hiding it from search and the profile page
        boolean isOwnProject = viewerDesignerId != null && viewerDesignerId.equals(project.getDesigner().getId());
        if (!project.isVisible() && !isOwnProject) {
            throw new ServiceException("That project couldn't be found.");
        }
        if (countThisView && !isOwnProject) {
            projectRepository.incrementViewCount(project.getId());
            // IDR-08 - also logged as its own row so a weekly delta is possible later
            projectViewRepository.save(new ProjectView(project));
        }
        return project;
    }

    // a designer's own project list, used wherever their portfolio is shown to someone else
    // (profile page, contact form, booking form)
    public List<Project> listForDesigner(Long designerId) {
        return projectRepository.findByDesignerIdOrderByCreatedAtDesc(designerId);
    }

    // CUS-12
    public Optional<BudgetEstimate> estimateBudget(String categoryName) {
        String cleanCategory = blankToNull(categoryName);
        if (cleanCategory == null) {
            return Optional.empty();
        }
        List<Project> withBudget = projectRepository.findByCategoryNameAndBudgetMinIsNotNull(cleanCategory);
        if (withBudget.isEmpty()) {
            return Optional.empty();
        }
        int min = withBudget.stream().mapToInt(Project::getBudgetMin).min().orElseThrow();
        int max = withBudget.stream().mapToInt(Project::getBudgetMax).max().orElseThrow();
        return Optional.of(new BudgetEstimate(min, max, withBudget.size()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // escapes LIKE wildcards (and the escape character itself) so a % or _ typed into a
    // search box is matched literally instead of acting as a wildcard - otherwise "%"
    // alone would match every project/designer. '!' is the escape character (matching
    // ProjectRepository.search()'s escape '!' clause), not a backslash - MySQL treats a
    // backslash inside a string literal as its own escape character, which risks an
    // unterminated literal there even though H2 (what the tests run against) accepts it
    private String escapeLike(String value) {
        return value == null ? null
                : value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
