package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.web.dto.ProjectForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// IDR-04 / IDR-05
@Service
public class ProjectService {

    private static final int MAX_PHOTOS = 10;

    private final ProjectRepository projectRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    public ProjectService(ProjectRepository projectRepository,
                          CategoryRepository categoryRepository,
                          FileStorageService fileStorageService) {
        this.projectRepository = projectRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public Project createProject(InteriorDesigner designer, ProjectForm form, List<MultipartFile> photos,
                                 List<MultipartFile> beforePhotos) {
        String title = InputRules.requireText(form.title(), "Title", 150);
        String description = InputRules.requireText(form.description(), "Description", 5000);

        if (form.categoryId() == null) {
            throw new ServiceException("Please select a category.");
        }
        Category category = categoryRepository.findById(form.categoryId())
                .orElseThrow(() -> new ServiceException("That category no longer exists."));

        List<MultipartFile> uploaded = cleanUploads(photos);
        if (uploaded.isEmpty()) {
            throw new ServiceException("Add at least one photo of the project.");
        }
        if (uploaded.size() > MAX_PHOTOS) {
            throw new ServiceException("You can upload up to " + MAX_PHOTOS + " photos.");
        }
        // before-photos are optional (IDR-07) - the "after" shots above are the only
        // required ones, same as before this story existed
        List<MultipartFile> uploadedBefore = cleanUploads(beforePhotos);
        if (uploadedBefore.size() > MAX_PHOTOS) {
            throw new ServiceException("You can upload up to " + MAX_PHOTOS + " before-photos.");
        }
        validateAll(uploaded, uploadedBefore);
        validateBudgetRange(form.budgetMin(), form.budgetMax());

        Project project = new Project(title, description, category, designer);
        project.updateDetails(title, description, category, form.budgetMin(), form.budgetMax(), cleanTimeline(form.estimatedTimeline()));
        uploaded.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        uploadedBefore.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        return projectRepository.save(project);
    }

    // IDR-07 - also where "project edit permission checks" (task 19.4) actually happen:
    // only the designer who owns the project can update it
    @Transactional
    public Project updateProject(InteriorDesigner designer, Long projectId, ProjectForm form,
                                 List<MultipartFile> afterPhotos, List<MultipartFile> beforePhotos) {
        Project project = findOwnedProject(designer, projectId);

        String title = InputRules.requireText(form.title(), "Title", 150);
        String description = InputRules.requireText(form.description(), "Description", 5000);

        if (form.categoryId() == null) {
            throw new ServiceException("Please select a category.");
        }
        Category category = categoryRepository.findById(form.categoryId())
                .orElseThrow(() -> new ServiceException("That category no longer exists."));
        validateBudgetRange(form.budgetMin(), form.budgetMax());

        List<MultipartFile> newAfterPhotos = cleanUploads(afterPhotos);
        List<MultipartFile> newBeforePhotos = cleanUploads(beforePhotos);
        if (project.getPhotoUrls().size() + newAfterPhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " photos in total.");
        }
        if (project.getBeforePhotoUrls().size() + newBeforePhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " before-photos in total.");
        }
        validateAll(newAfterPhotos, newBeforePhotos);

        project.updateDetails(title, description, category, form.budgetMin(), form.budgetMax(), cleanTimeline(form.estimatedTimeline()));
        newAfterPhotos.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        newBeforePhotos.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        return project;
    }

    // IDR-09
    @Transactional
    public Project advanceMilestone(InteriorDesigner designer, Long projectId) {
        Project project = findOwnedProject(designer, projectId);
        try {
            project.advanceMilestone();
        } catch (IllegalStateException e) {
            throw new ServiceException(e.getMessage());
        }
        return project;
    }

    private Project findOwnedProject(InteriorDesigner designer, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("Project not found."));
        if (!project.getDesigner().getId().equals(designer.getId())) {
            throw new ServiceException("You can only manage your own projects.");
        }
        return project;
    }

    private List<MultipartFile> cleanUploads(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    // IDR-15 - check every file before storing any of them, so a bad file later in
    // the batch doesn't leave earlier valid ones already written to disk
    private void validateAll(List<MultipartFile> afterPhotos, List<MultipartFile> beforePhotos) {
        afterPhotos.forEach(fileStorageService::validate);
        beforePhotos.forEach(fileStorageService::validate);
    }

    // IDR-14
    private void validateBudgetRange(Integer budgetMin, Integer budgetMax) {
        if (budgetMin == null && budgetMax == null) {
            return;
        }
        if (budgetMin == null || budgetMax == null) {
            throw new ServiceException("Enter both a minimum and maximum budget, or leave both blank.");
        }
        if (budgetMin < 0 || budgetMax < 0) {
            throw new ServiceException("Budget can't be negative.");
        }
        if (budgetMin > budgetMax) {
            throw new ServiceException("Minimum budget can't be more than the maximum.");
        }
    }

    private String cleanTimeline(String estimatedTimeline) {
        if (estimatedTimeline == null || estimatedTimeline.isBlank()) {
            return null;
        }
        String trimmed = estimatedTimeline.trim();
        if (trimmed.length() > 100) {
            throw new ServiceException("Estimated timeline must be under 100 characters.");
        }
        return trimmed;
    }
}
