package com.csit314.platform.repository;

import com.csit314.platform.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByDesignerIdOrderByCreatedAtDesc(Long designerId);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    long countByDesignerId(Long designerId);
}
