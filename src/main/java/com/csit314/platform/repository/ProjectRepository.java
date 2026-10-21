package com.csit314.platform.repository;

import com.csit314.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByDesignerIdOrderByCreatedAtDesc(Long designerId);
}
