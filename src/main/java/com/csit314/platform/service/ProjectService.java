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

        Project project = new Project(title, description, category, designer);
        uploaded.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        uploadedBefore.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        return projectRepository.save(project);
    }

    // IDR-07 - also where "project edit permission checks" (task 19.4) actually happen:
    // only the designer who owns the project can update it
    @Transactional
    public Project updateProject(InteriorDesigner designer, Long projectId, ProjectForm form,
                                 List<MultipartFile> afterPhotos, List<MultipartFile> beforePhotos) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ServiceException("Project not found."));
        if (!project.getDesigner().getId().equals(designer.getId())) {
            throw new ServiceException("You can only edit your own projects.");
        }

        String title = InputRules.requireText(form.title(), "Title", 150);
        String description = InputRules.requireText(form.description(), "Description", 5000);

        if (form.categoryId() == null) {
            throw new ServiceException("Please select a category.");
        }
        Category category = categoryRepository.findById(form.categoryId())
                .orElseThrow(() -> new ServiceException("That category no longer exists."));

        project.updateDetails(title, description, category);

        List<MultipartFile> newAfterPhotos = cleanUploads(afterPhotos);
        List<MultipartFile> newBeforePhotos = cleanUploads(beforePhotos);
        if (project.getPhotoUrls().size() + newAfterPhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " photos in total.");
        }
        if (project.getBeforePhotoUrls().size() + newBeforePhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " before-photos in total.");
        }

        newAfterPhotos.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        newBeforePhotos.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        return project;
    }

    private List<MultipartFile> cleanUploads(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }
}
