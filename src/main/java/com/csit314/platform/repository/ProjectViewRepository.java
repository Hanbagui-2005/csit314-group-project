package com.csit314.platform.repository;

import com.csit314.platform.model.ProjectView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ProjectViewRepository extends JpaRepository<ProjectView, Long> {

    long countByProjectDesignerIdAndViewedAtAfter(Long designerId, LocalDateTime since);
}
