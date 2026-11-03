package com.csit314.platform.repository;

import com.csit314.platform.model.ProjectFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectFlagRepository extends JpaRepository<ProjectFlag, Long> {

    // PM-11 - most recent first, so PM sees new reports without having to scroll
    List<ProjectFlag> findAllByOrderByCreatedAtDesc();
}
