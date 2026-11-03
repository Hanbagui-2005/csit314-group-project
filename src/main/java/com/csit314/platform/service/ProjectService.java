package com.csit314.platform.service;

import com.csit314.platform.model.Category;
import com.csit314.platform.model.InteriorDesigner;
import com.csit314.platform.model.Project;
import com.csit314.platform.repository.CategoryRepository;
import com.csit314.platform.repository.ProjectRepository;
import com.csit314.platform.dto.ProjectForm;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// IDR-04 / IDR-05
@Service
public class ProjectService {

    private static final int MAX_PHOTOS = 10;

    private final ProjectRepository projectRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final ImageSimilarityService imageSimilarityService;

    public ProjectService(ProjectRepository projectRepository,
                          CategoryRepository categoryRepository,
                          FileStorageService fileStorageService,
                          ImageSimilarityService imageSimilarityService) {
        this.projectRepository = projectRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.imageSimilarityService = imageSimilarityService;
    }

    // IDR-04/PM-13 - category dropdown on the new-project form; a deactivated category
    // isn't offered for new work
    public List<Category> listCategories() {
        return categoryRepository.findByActiveTrue(Sort.unsorted());
    }

    // IDR-07/PM-13 - category dropdown on the edit form: active categories, plus the
    // project's own category even if it's since been deactivated. Without that, a
    // deactivated category wouldn't be selected in the dropdown at all, the browser
    // would default to whatever option happens to be first, and saving the edit - even
    // just to fix a typo - would silently move the project to an unrelated category
    public List<Category> listCategoriesForEdit(Project project) {
        List<Category> active = listCategories();
        Category current = project.getCategory();
        if (current.isActive() || active.stream().anyMatch(c -> c.getId().equals(current.getId()))) {
            return active;
        }
        List<Category> withCurrent = new ArrayList<>(active);
        withCurrent.add(current);
        return withCurrent;
    }

    // a designer's own dashboard list
    public List<Project> listForDesigner(Long designerId) {
        return projectRepository.findByDesignerIdOrderByCreatedAtDesc(designerId);
    }

    // IDR-07 - the edit form's GET: empty when the project doesn't exist or isn't owned
    // by this designer, so the controller can redirect instead of throwing
    public Optional<Project> findEditable(InteriorDesigner designer, Long projectId) {
        return projectRepository.findById(projectId)
                .filter(project -> project.getDesigner().getId().equals(designer.getId()));
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
        // PM-13 - a new project can't be given a deactivated category, hand-crafted POST
        // or not; there's no "it already had this one" exception to make here
        if (!category.isActive()) {
            throw new ServiceException("That category is no longer available.");
        }

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
        validateRoomSize(form.roomSizeSqm());

        Project project = new Project(title, description, category, designer);
        project.updateDetails(title, description, category, form.budgetMin(), form.budgetMax(),
                cleanTimeline(form.estimatedTimeline()), form.roomSizeSqm());
        uploaded.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        uploadedBefore.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        Project saved = projectRepository.save(project);
        // PM-04 - checked once the project (and so its photo URLs) actually has an id.
        // Both lists line up 1:1 with their own upload list since each was empty
        // beforehand - before-photos are checked too, not just the "after" shots,
        // since a re-uploaded photo can be plagiarised either way
        checkForPlagiarism(saved, uploaded, saved.getPhotoUrls());
        checkForPlagiarism(saved, uploadedBefore, saved.getBeforePhotoUrls());
        return saved;
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
        // PM-13 - switching TO a deactivated category is blocked, but keeping the
        // project's current one (even if it's since been deactivated) is still allowed -
        // otherwise there'd be no way to edit anything else about an older project
        // without being forced to also change its category
        if (!category.isActive() && !category.getId().equals(project.getCategory().getId())) {
            throw new ServiceException("That category is no longer available.");
        }
        validateBudgetRange(form.budgetMin(), form.budgetMax());
        validateRoomSize(form.roomSizeSqm());

        List<MultipartFile> newAfterPhotos = cleanUploads(afterPhotos);
        List<MultipartFile> newBeforePhotos = cleanUploads(beforePhotos);
        if (project.getPhotoUrls().size() + newAfterPhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " photos in total.");
        }
        if (project.getBeforePhotoUrls().size() + newBeforePhotos.size() > MAX_PHOTOS) {
            throw new ServiceException("A project can have up to " + MAX_PHOTOS + " before-photos in total.");
        }
        validateAll(newAfterPhotos, newBeforePhotos);

        project.updateDetails(title, description, category, form.budgetMin(), form.budgetMax(),
                cleanTimeline(form.estimatedTimeline()), form.roomSizeSqm());
        newAfterPhotos.forEach(file -> project.addPhoto(fileStorageService.store(file)));
        newBeforePhotos.forEach(file -> project.addBeforePhoto(fileStorageService.store(file)));

        // PM-04 - only the newly-added photos (the last N entries of each list), not
        // the ones the project already had before this edit. Before-photos are checked
        // too, same as in createProject
        List<String> allAfterUrls = project.getPhotoUrls();
        List<String> newAfterUrls = allAfterUrls.subList(allAfterUrls.size() - newAfterPhotos.size(), allAfterUrls.size());
        checkForPlagiarism(project, newAfterPhotos, newAfterUrls);
        List<String> allBeforeUrls = project.getBeforePhotoUrls();
        List<String> newBeforeUrls = allBeforeUrls.subList(allBeforeUrls.size() - newBeforePhotos.size(), allBeforeUrls.size());
        checkForPlagiarism(project, newBeforePhotos, newBeforeUrls);

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

    // PM-04 - files and newUrls are the same size and in the same order (both built by
    // iterating the same upload list), so they're paired up by index
    private void checkForPlagiarism(Project project, List<MultipartFile> files, List<String> newUrls) {
        for (int i = 0; i < files.size(); i++) {
            byte[] bytes;
            try {
                bytes = files.get(i).getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            imageSimilarityService.checkAndRecord(project, newUrls.get(i), bytes);
        }
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

    private void validateRoomSize(Integer roomSizeSqm) {
        if (roomSizeSqm != null && roomSizeSqm <= 0) {
            throw new ServiceException("Room size must be greater than zero.");
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
