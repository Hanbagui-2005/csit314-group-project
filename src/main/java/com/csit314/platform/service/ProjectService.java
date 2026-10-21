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
    public Project createProject(InteriorDesigner designer, ProjectForm form, List<MultipartFile> photos) {
        String title = InputRules.requireText(form.title(), "Title", 150);
        String description = InputRules.requireText(form.description(), "Description", 5000);

        if (form.categoryId() == null) {
            throw new ServiceException("Please select a category.");
        }
        Category category = categoryRepository.findById(form.categoryId())
                .orElseThrow(() -> new ServiceException("That category no longer exists."));

        List<MultipartFile> uploaded = photos == null ? List.of()
                : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (uploaded.isEmpty()) {
            throw new ServiceException("Add at least one photo of the project.");
        }
        if (uploaded.size() > MAX_PHOTOS) {
            throw new ServiceException("You can upload up to " + MAX_PHOTOS + " photos.");
        }

        Project project = new Project(title, description, category, designer);
        uploaded.forEach(file -> project.addPhoto(fileStorageService.store(file)));

        return projectRepository.save(project);
    }
}
